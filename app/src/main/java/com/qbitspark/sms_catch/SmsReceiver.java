package com.qbitspark.sms_catch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
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

                    for (Object pdu : pdus) {
                        SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);

                        // Extract message details
                        String sender = smsMessage.getOriginatingAddress();
                        String messageBody = smsMessage.getMessageBody();
                        long timestamp = smsMessage.getTimestampMillis();

                        // Set message data
                        messageData.setSender(sender);
                        messageData.setMessageBody(messageBody);
                        messageData.setTimestamp(timestamp);
                    }

                    // Start the service and pass the message data
                    Intent serviceIntent = new Intent(context, SmsListenerService.class);
                    serviceIntent.putExtra("sender", messageData.getSender());
                    serviceIntent.putExtra("messageBody", messageData.getMessageBody());
                    serviceIntent.putExtra("timestamp", messageData.getTimestamp());

                    // For Android 8.0+, use startForegroundService
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    }

                    Log.d(TAG, "SMS dispatched to service: " + messageData.getSender());
                }
            }
        }
    }
}