package com.qbitspark.sms_catch;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WorkManagerHelper {
    private static final String PERIODIC_SYNC_TAG = "PERIODIC_SMS_SYNC";
    private static final String IMMEDIATE_SYNC_TAG = "IMMEDIATE_SMS_SYNC";

    public static void scheduleSyncWorker(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Schedule periodic work every 15 minutes (minimum interval)
        PeriodicWorkRequest syncWorkRequest = new PeriodicWorkRequest.Builder(
                SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag(PERIODIC_SYNC_TAG)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_TAG,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing to avoid duplicates
                syncWorkRequest);
    }

    public static void triggerImmediateSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .addTag(IMMEDIATE_SYNC_TAG)
                .build();

        // Use REPLACE policy to avoid queueing multiple immediate syncs
        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_TAG,
                ExistingWorkPolicy.REPLACE,
                syncRequest);
    }
}