package com.qbitspark.sms_catch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null && intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Log.d(TAG, "Boot completed, starting services");

            // Start the SMS listener service
            Intent serviceIntent = new Intent(context, SmsListenerService.class);

            // For Android 8.0 (Oreo) and higher, we need to start the service as a foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            // Trigger an immediate sync first
            WorkManagerHelper.triggerImmediateSync(context);
            // Also restart the periodic sync worker
            WorkManagerHelper.scheduleSyncWorker(context);
        }
    }
}
