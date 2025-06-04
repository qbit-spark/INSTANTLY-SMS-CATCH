package com.qbitspark.sms_catch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "UpdatedSmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(SMS_RECEIVED)) {

            // FIRST: Check for SIM swaps before processing message
            EnhancedSIMManager simManager = new EnhancedSIMManager(context);
            EnhancedSIMManager.SwapDetectionResult swapResult = simManager.detectSIMChanges();

            if (swapResult.hasChanges()) {
                Log.w(TAG, "SIM changes detected: " + swapResult.getChangesSummary());
                // You could trigger a notification or alert here
                handleSIMChanges(context, swapResult);
            }

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

                    // Get receiver identifier using ICCID-based approach
                    String receiverIdentifier = getReceiverIdentifierWithICCID(context, intent);

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
                    Log.d(TAG, "Receiver (ICCID-based): " + messageData.getReceiver());
                    Log.d(TAG, "Full message: " + messageData.getMessageBody());
                }
            }
        }
    }

    /**
     * Enhanced receiver identification using ICCID tracking
     */
    private String getReceiverIdentifierWithICCID(Context context, Intent intent) {
        try {
            // Get subscription ID from intent
            int subId = getSubscriptionIdFromIntent(intent);

            if (subId != -1) {
                // Use enhanced SIM manager to get SIM info
                EnhancedSIMManager simManager = new EnhancedSIMManager(context);
                EnhancedSIMManager.SIMInfo simInfo = simManager.getSIMBySubscriptionId(subId);

                if (simInfo != null) {
                    // Create comprehensive identifier with ICCID
                    String identifier = createComprehensiveIdentifier(simInfo);
                    Log.d(TAG, "Created ICCID-based identifier: " + identifier);
                    return identifier;
                } else {
                    Log.w(TAG, "No SIM info found for subscription ID: " + subId);
                    return "UNKNOWN_SIM_" + subId;
                }
            } else {
                Log.w(TAG, "No subscription ID found in intent");
                return "NO_SUBSCRIPTION_ID";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error creating ICCID-based identifier", e);
            return "ERROR_GETTING_IDENTIFIER";
        }
    }

    /**
     * Extract subscription ID from SMS intent
     */
    private int getSubscriptionIdFromIntent(Intent intent) {
        // Try multiple possible keys
        if (intent.hasExtra("subscription")) {
            return intent.getIntExtra("subscription", -1);
        } else if (intent.hasExtra("android.telephony.extra.SUBSCRIPTION_INDEX")) {
            return intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
        } else if (intent.hasExtra("phone")) {
            return intent.getIntExtra("phone", -1);
        }
        return -1;
    }

    /**
     * Create comprehensive identifier using ICCID and user data
     */
    private String createComprehensiveIdentifier(EnhancedSIMManager.SIMInfo simInfo) {
        StringBuilder identifier = new StringBuilder();

        // Start with user's phone number (most important for API)
        if (simInfo.userPhoneNumber != null && !simInfo.userPhoneNumber.isEmpty()) {
            identifier.append(simInfo.userPhoneNumber);
        } else {
            identifier.append("NO_PHONE");
        }

        // Add carrier and slot info
        identifier.append("_").append(simInfo.carrierName)
                .append("_SLOT").append(simInfo.slotIndex);

        // Add masked ICCID for uniqueness (last 6 digits)
        if (simInfo.iccid != null && simInfo.iccid.length() >= 6) {
            String maskedICCID = simInfo.iccid.substring(simInfo.iccid.length() - 6);
            identifier.append("_").append(maskedICCID);
        }

        return identifier.toString();
    }

    /**
     * Handle detected SIM changes
     */
    private void handleSIMChanges(Context context, EnhancedSIMManager.SwapDetectionResult swapResult) {
        // Log the changes
        for (EnhancedSIMManager.SIMInfo newSIM : swapResult.newSIMs) {
            Log.i(TAG, "🆕 NEW SIM: " + newSIM.carrierName + " in slot " + newSIM.slotIndex);
        }

        for (EnhancedSIMManager.SIMInfo removedSIM : swapResult.removedSIMs) {
            Log.i(TAG, "❌ REMOVED SIM: " + removedSIM.getDisplayName());
        }

        for (EnhancedSIMManager.SIMInfo movedSIM : swapResult.movedSIMs) {
            Log.i(TAG, "🔄 MOVED SIM: " + movedSIM.getDisplayName() + " to slot " + movedSIM.slotIndex);
        }

        // You could add more sophisticated handling here:
        // - Send notification to user about SIM changes
        // - Alert your backend about potential security issue
        // - Force user to reconfigure if needed

        // Example: Send alert to your API
        if (!swapResult.newSIMs.isEmpty() || !swapResult.removedSIMs.isEmpty()) {
            // This is a significant change - new or removed SIMs
            Log.w(TAG, "⚠️ SECURITY ALERT: SIM configuration changed!");
            // You could call your API here to report this
        }
    }
}