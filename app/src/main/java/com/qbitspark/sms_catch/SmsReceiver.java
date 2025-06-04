package com.qbitspark.sms_catch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(SMS_RECEIVED)) {
            Bundle bundle = intent.getExtras();

            if (bundle != null) {
                // Get the SMS message array
                Object[] pdus = (Object[]) bundle.get("pdus");
                if (pdus != null) {
                    // Create a MessageData object
                    final MessageData messageData = new MessageData();

                    // Initialize variables to store concatenated message
                    StringBuilder fullMessage = new StringBuilder();
                    String sender = null;
                    long timestamp = 0;

                    for (Object pdu : pdus) {
                        SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);

                        // Get sender and timestamp from first PDU
                        if (sender == null) {
                            sender = smsMessage.getOriginatingAddress();
                            timestamp = smsMessage.getTimestampMillis();
                        }

                        // Concatenate message bodies from all PDUs
                        fullMessage.append(smsMessage.getMessageBody());
                    }

                    // Get receiver number/identifier automatically
                    String receiverIdentifier = getReceiverIdentifier(context, intent);

                    // Set the complete message data
                    messageData.setSender(sender);
                    messageData.setReceiver(receiverIdentifier);
                    messageData.setMessageBody(fullMessage.toString());
                    messageData.setTimestamp(timestamp);

                    // Start the service and pass the message data
                    Intent serviceIntent = new Intent(context, SmsListenerService.class);
                    serviceIntent.putExtra("sender", messageData.getSender());
                    serviceIntent.putExtra("receiver", messageData.getReceiver());
                    serviceIntent.putExtra("messageBody", messageData.getMessageBody());
                    serviceIntent.putExtra("timestamp", messageData.getTimestamp());

                    // For Android 8.0+, use startForegroundService
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    }

                    Log.d(TAG, "SMS dispatched to service: " + messageData.getSender());
                    Log.d(TAG, "Receiver: " + messageData.getReceiver());
                    Log.d(TAG, "Full message: " + messageData.getMessageBody());
                }
            }
        }
    }

    private String getReceiverIdentifier(Context context, Intent intent) {
        String receiverIdentifier = "Unknown";

        try {
            // Get subscription ID from intent (this works reliably)
            int subId = -1;
            if (intent.hasExtra("subscription")) {
                subId = intent.getIntExtra("subscription", -1);
                Log.d(TAG, "Found subscription ID in intent: " + subId);
            } else if (intent.hasExtra("android.telephony.extra.SUBSCRIPTION_INDEX")) {
                subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
                Log.d(TAG, "Found subscription ID in SUBSCRIPTION_INDEX: " + subId);
            }

            if (subId != -1) {
                receiverIdentifier = createIdentifierForSubscription(context, subId);
            } else {
                Log.w(TAG, "No subscription ID found in intent");
                receiverIdentifier = "UNKNOWN_SIM";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting receiver identifier", e);
            receiverIdentifier = "ERROR_SIM";
        }

        return receiverIdentifier;
    }

    private String createIdentifierForSubscription(Context context, int subId) {
        try {
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
                    SubscriptionInfo subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subId);

                    if (subscriptionInfo != null) {
                        String carrierName = subscriptionInfo.getCarrierName().toString();
                        String phoneNumber = null;

                        // Try multiple methods to get the phone number

                        // Method 1: From SubscriptionInfo
                        phoneNumber = subscriptionInfo.getNumber();
                        if (phoneNumber != null && !phoneNumber.isEmpty()) {
                            Log.d(TAG, "Got phone number from SubscriptionInfo: " + phoneNumber);
                        }

                        // Method 2: From TelephonyManager for this subscription
                        if (phoneNumber == null || phoneNumber.isEmpty()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    android.telephony.TelephonyManager telephonyManager =
                                            (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                                    android.telephony.TelephonyManager subTelephonyManager =
                                            telephonyManager.createForSubscriptionId(subId);
                                    if (subTelephonyManager != null) {
                                        phoneNumber = subTelephonyManager.getLine1Number();
                                        Log.d(TAG, "TelephonyManager for subId " + subId + " returned: " + (phoneNumber != null ? phoneNumber : "null"));
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Error getting number from TelephonyManager for subId " + subId, e);
                                }
                            }
                        }

                        // Method 3: From display name (sometimes contains number)
                        if (phoneNumber == null || phoneNumber.isEmpty()) {
                            String displayName = subscriptionInfo.getDisplayName().toString();
                            if (displayName != null && displayName.matches(".*\\+?\\d{10,15}.*")) {
                                phoneNumber = displayName.replaceAll("[^+\\d]", "");
                                Log.d(TAG, "Extracted number from display name: " + phoneNumber);
                            }
                        }

                        // Method 4: Check if user has manually saved a phone number for this SIM
                        if (phoneNumber == null || phoneNumber.isEmpty()) {
                            SharedPreferences simPrefs = context.getSharedPreferences("SIM_PHONE_NUMBERS", Context.MODE_PRIVATE);
                            String simKey = carrierName + "_SIM_" + subId;
                            phoneNumber = simPrefs.getString(simKey, null);
                            if (phoneNumber != null && !phoneNumber.isEmpty()) {
                                Log.d(TAG, "Using saved phone number for " + simKey + ": " + phoneNumber);
                            }
                        }

                        // Create the final identifier
                        if (phoneNumber != null && !phoneNumber.isEmpty()) {
                            String identifier = phoneNumber + "_" + carrierName + "_SIM_" + subId;
                            Log.d(TAG, "Created full identifier: " + identifier);
                            return identifier;
                        } else {
                            String identifier = carrierName + "_SIM_" + subId;
                            Log.d(TAG, "Created basic identifier: " + identifier);
                            return identifier;
                        }
                    } else {
                        Log.w(TAG, "No subscription info found for subId: " + subId);
                        return "SIM_" + subId;
                    }
                } else {
                    Log.w(TAG, "Android version too old for SubscriptionManager");
                    return "SIM_" + subId;
                }
            } else {
                Log.w(TAG, "No READ_PHONE_STATE permission");
                return "SIM_" + subId + "_NO_PERMISSION";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating identifier for subscription " + subId, e);
            return "SIM_" + subId + "_ERROR";
        }
    }
}