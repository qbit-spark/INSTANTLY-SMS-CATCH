// Create new file: SIMMonitorService.java

package com.qbitspark.sms_catch;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

public class SIMMonitorService extends Service {
    private static final String TAG = "SIMMonitorService";
    private static final int CHECK_INTERVAL = 5000; // Check every 5 seconds

    private Handler handler;
    private Runnable simCheckRunnable;
    private EnhancedSIMManager simManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🔍 SIM Monitor Service started");

        simManager = new EnhancedSIMManager(this);
        handler = new Handler(Looper.getMainLooper());

        // Create periodic check runnable
        simCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkForSIMChanges();
                // Schedule next check
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "🚀 Starting SIM monitoring...");

        // Start periodic SIM checking
        handler.post(simCheckRunnable);

        return START_STICKY; // Restart if killed
    }

    private void checkForSIMChanges() {
        try {
            EnhancedSIMManager.SwapDetectionResult result = simManager.detectSIMChanges();

            if (result.hasChanges()) {
                Log.w(TAG, "⚠️ REAL-TIME SIM CHANGES DETECTED!");
                Log.w(TAG, result.getChangesSummary());

                // Handle different types of changes
                if (!result.removedSIMs.isEmpty()) {
                    for (EnhancedSIMManager.SIMInfo removedSIM : result.removedSIMs) {
                        Log.e(TAG, "🚨 IMMEDIATE SIM REMOVAL: " + removedSIM.getDisplayName());

                        // Send immediate alert
                        sendSIMRemovalAlert(removedSIM);
                    }
                }

                if (!result.newSIMs.isEmpty()) {
                    for (EnhancedSIMManager.SIMInfo newSIM : result.newSIMs) {
                        Log.i(TAG, "📱 NEW SIM INSERTED: " + newSIM.carrierName);

                        // Send new SIM alert
                        sendNewSIMAlert(newSIM);
                    }
                }

                if (!result.movedSIMs.isEmpty()) {
                    for (EnhancedSIMManager.SIMInfo movedSIM : result.movedSIMs) {
                        Log.i(TAG, "🔄 SIM MOVED: " + movedSIM.getDisplayName() + " to slot " + movedSIM.slotIndex);
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "💥 Error during SIM check", e);
        }
    }

    private void sendSIMRemovalAlert(EnhancedSIMManager.SIMInfo removedSIM) {
        // Option 1: Send to your API immediately
        // ApiClient.sendAlert(this, "SIM_REMOVED", removedSIM);

        // Option 2: Show notification
        // NotificationHelper.showSIMRemovedNotification(this, removedSIM);

        // Option 3: Broadcast to other parts of app
        Intent broadcast = new Intent("SIM_REMOVED");
        broadcast.putExtra("sim_iccid", removedSIM.iccid);
        broadcast.putExtra("sim_phone", removedSIM.userPhoneNumber);
        sendBroadcast(broadcast);

        Log.e(TAG, "🚨 SECURITY ALERT: SIM removed - " + removedSIM.userPhoneNumber);
    }

    private void sendNewSIMAlert(EnhancedSIMManager.SIMInfo newSIM) {
        Intent broadcast = new Intent("NEW_SIM_DETECTED");
        broadcast.putExtra("sim_iccid", newSIM.iccid);
        broadcast.putExtra("sim_carrier", newSIM.carrierName);
        sendBroadcast(broadcast);

        Log.w(TAG, "🆕 New SIM alert: " + newSIM.carrierName);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "🛑 SIM Monitor Service stopped");

        // Stop periodic checks
        if (handler != null && simCheckRunnable != null) {
            handler.removeCallbacks(simCheckRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding
    }
}