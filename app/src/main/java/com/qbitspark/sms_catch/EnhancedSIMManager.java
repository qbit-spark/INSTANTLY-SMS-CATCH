package com.qbitspark.sms_catch;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced SIM Manager with ICCID-based swap detection
 */
public class EnhancedSIMManager {
    private static final String TAG = "EnhancedSIMManager";
    private static final String PREFS_SIM_DATA = "SIM_DATA_V2"; // New preference file
    private final Context context;

    public EnhancedSIMManager(Context context) {
        this.context = context;
    }

    /**
     * Comprehensive SIM information class with ICCID tracking
     */
    public static class SIMInfo {
        public String iccid;              // Primary identifier - follows SIM card
        public String userPhoneNumber;    // User-entered phone number
        public String carrierName;        // Network operator name
        public int subscriptionId;        // Current subscription ID (can change)
        public int slotIndex;            // Current slot (0 or 1) - can change
        public String detectedNumber;     // Auto-detected number (may be null)
        public long lastSeen;            // When this SIM was last detected
        public boolean isActive;         // Currently present in device

        public String getDisplayName() {
            return carrierName + " (" + (userPhoneNumber != null ? userPhoneNumber : "No Number") + ")";
        }

        public String getIdentifierKey() {
            return "SIM_" + iccid;
        }
    }

    /**
     * SIM swap detection result
     */
    public static class SwapDetectionResult {
        public List<SIMInfo> newSIMs = new ArrayList<>();
        public List<SIMInfo> removedSIMs = new ArrayList<>();
        public List<SIMInfo> movedSIMs = new ArrayList<>();
        public List<SIMInfo> activeSIMs = new ArrayList<>();

        public boolean hasChanges() {
            return !newSIMs.isEmpty() || !removedSIMs.isEmpty() || !movedSIMs.isEmpty();
        }

        public String getChangesSummary() {
            StringBuilder summary = new StringBuilder();
            if (!newSIMs.isEmpty()) {
                summary.append("📱 New SIMs detected: ").append(newSIMs.size()).append("\n");
            }
            if (!removedSIMs.isEmpty()) {
                summary.append("❌ SIMs removed: ").append(removedSIMs.size()).append("\n");
            }
            if (!movedSIMs.isEmpty()) {
                summary.append("🔄 SIMs moved slots: ").append(movedSIMs.size()).append("\n");
            }
            return summary.toString();
        }
    }

    /**
     * Detect current SIMs and compare with saved data
     */
    public SwapDetectionResult detectSIMChanges() {
        SwapDetectionResult result = new SwapDetectionResult();

        // Get currently detected SIMs
        List<SIMInfo> currentSIMs = getCurrentSIMs();

        // Get previously saved SIMs
        List<SIMInfo> savedSIMs = getSavedSIMs();

        Log.d(TAG, "Detected " + currentSIMs.size() + " current SIMs, " + savedSIMs.size() + " saved SIMs");

        // Check each current SIM
        for (SIMInfo currentSIM : currentSIMs) {
            SIMInfo savedSIM = findSIMByICCID(savedSIMs, currentSIM.iccid);

            if (savedSIM == null) {
                // New SIM card
                result.newSIMs.add(currentSIM);
                Log.d(TAG, "New SIM detected: " + currentSIM.iccid);
            } else {
                // Existing SIM - check if it moved slots
                if (savedSIM.slotIndex != currentSIM.slotIndex) {
                    currentSIM.userPhoneNumber = savedSIM.userPhoneNumber; // Preserve user data
                    result.movedSIMs.add(currentSIM);
                    Log.d(TAG, "SIM moved from slot " + savedSIM.slotIndex + " to " + currentSIM.slotIndex);
                } else {
                    // Same SIM, same slot - preserve user data
                    currentSIM.userPhoneNumber = savedSIM.userPhoneNumber;
                }
            }

            result.activeSIMs.add(currentSIM);
        }

        // Check for removed SIMs
        for (SIMInfo savedSIM : savedSIMs) {
            if (findSIMByICCID(currentSIMs, savedSIM.iccid) == null) {
                result.removedSIMs.add(savedSIM);
                Log.d(TAG, "SIM removed: " + savedSIM.iccid);
            }
        }

        // Update saved data with current state
        if (result.hasChanges()) {
            saveSIMs(result.activeSIMs);
            Log.d(TAG, "Updated SIM database due to changes");
        }

        return result;
    }

    /**
     * Get currently active SIMs from system
     */
    private List<SIMInfo> getCurrentSIMs() {
        List<SIMInfo> currentSIMs = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager subscriptionManager = SubscriptionManager.from(context);
                List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

                if (subscriptions != null) {
                    for (SubscriptionInfo subInfo : subscriptions) {
                        SIMInfo simInfo = new SIMInfo();
                        simInfo.iccid = subInfo.getIccId();
                        simInfo.carrierName = subInfo.getCarrierName().toString();
                        simInfo.subscriptionId = subInfo.getSubscriptionId();
                        simInfo.slotIndex = subInfo.getSimSlotIndex();
                        simInfo.detectedNumber = subInfo.getNumber();
                        simInfo.lastSeen = System.currentTimeMillis();
                        simInfo.isActive = true;

                        currentSIMs.add(simInfo);

                        Log.d(TAG, "Detected SIM: ICCID=" + maskICCID(simInfo.iccid) +
                                ", Carrier=" + simInfo.carrierName +
                                ", Slot=" + simInfo.slotIndex);
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied for SIM detection", e);
            }
        }

        return currentSIMs;
    }

    /**
     * Save SIM configuration with phone number
     */
    public void saveSIMConfiguration(String iccid, String phoneNumber) {
        List<SIMInfo> sims = getSavedSIMs();
        boolean found = false;

        for (SIMInfo sim : sims) {
            if (sim.iccid.equals(iccid)) {
                sim.userPhoneNumber = phoneNumber;
                found = true;
                break;
            }
        }

        if (!found) {
            // Create new entry if not found
            SIMInfo newSim = new SIMInfo();
            newSim.iccid = iccid;
            newSim.userPhoneNumber = phoneNumber;
            newSim.lastSeen = System.currentTimeMillis();
            sims.add(newSim);
        }

        saveSIMs(sims);
        Log.d(TAG, "Saved phone number for ICCID: " + maskICCID(iccid));
    }

    /**
     * Get SIM information by subscription ID (for message processing)
     */
    public SIMInfo getSIMBySubscriptionId(int subscriptionId) {
        List<SIMInfo> activeSIMs = getCurrentSIMs();

        for (SIMInfo sim : activeSIMs) {
            if (sim.subscriptionId == subscriptionId) {
                // Try to get saved phone number
                List<SIMInfo> savedSIMs = getSavedSIMs();
                SIMInfo savedSIM = findSIMByICCID(savedSIMs, sim.iccid);
                if (savedSIM != null) {
                    sim.userPhoneNumber = savedSIM.userPhoneNumber;
                }
                return sim;
            }
        }

        return null;
    }

    /**
     * Check if all SIMs are configured with phone numbers
     */
    public boolean areAllSIMsConfigured() {
        List<SIMInfo> currentSIMs = getCurrentSIMs();
        List<SIMInfo> savedSIMs = getSavedSIMs();

        for (SIMInfo currentSIM : currentSIMs) {
            SIMInfo savedSIM = findSIMByICCID(savedSIMs, currentSIM.iccid);
            if (savedSIM == null || savedSIM.userPhoneNumber == null || savedSIM.userPhoneNumber.isEmpty()) {
                return false;
            }
        }

        return !currentSIMs.isEmpty(); // At least one SIM must be present
    }

    /**
     * Get next unconfigured SIM
     */
    public SIMInfo getNextUnconfiguredSIM() {
        List<SIMInfo> currentSIMs = getCurrentSIMs();
        List<SIMInfo> savedSIMs = getSavedSIMs();

        for (SIMInfo currentSIM : currentSIMs) {
            SIMInfo savedSIM = findSIMByICCID(savedSIMs, currentSIM.iccid);
            if (savedSIM == null || savedSIM.userPhoneNumber == null || savedSIM.userPhoneNumber.isEmpty()) {
                return currentSIM;
            }
        }

        return null;
    }

    // Helper methods
    private SIMInfo findSIMByICCID(List<SIMInfo> sims, String iccid) {
        for (SIMInfo sim : sims) {
            if (sim.iccid.equals(iccid)) {
                return sim;
            }
        }
        return null;
    }

    private void saveSIMs(List<SIMInfo> sims) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SIM_DATA, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // Clear existing data
        editor.clear();

        // Save each SIM
        for (int i = 0; i < sims.size(); i++) {
            SIMInfo sim = sims.get(i);
            String prefix = "sim_" + i + "_";

            editor.putString(prefix + "iccid", sim.iccid);
            editor.putString(prefix + "userPhoneNumber", sim.userPhoneNumber);
            editor.putString(prefix + "carrierName", sim.carrierName);
            editor.putInt(prefix + "subscriptionId", sim.subscriptionId);
            editor.putInt(prefix + "slotIndex", sim.slotIndex);
            editor.putString(prefix + "detectedNumber", sim.detectedNumber);
            editor.putLong(prefix + "lastSeen", sim.lastSeen);
            editor.putBoolean(prefix + "isActive", sim.isActive);
        }

        editor.putInt("sim_count", sims.size());
        editor.apply();
    }

    private List<SIMInfo> getSavedSIMs() {
        List<SIMInfo> sims = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_SIM_DATA, Context.MODE_PRIVATE);

        int count = prefs.getInt("sim_count", 0);

        for (int i = 0; i < count; i++) {
            String prefix = "sim_" + i + "_";

            SIMInfo sim = new SIMInfo();
            sim.iccid = prefs.getString(prefix + "iccid", null);
            sim.userPhoneNumber = prefs.getString(prefix + "userPhoneNumber", null);
            sim.carrierName = prefs.getString(prefix + "carrierName", null);
            sim.subscriptionId = prefs.getInt(prefix + "subscriptionId", -1);
            sim.slotIndex = prefs.getInt(prefix + "slotIndex", -1);
            sim.detectedNumber = prefs.getString(prefix + "detectedNumber", null);
            sim.lastSeen = prefs.getLong(prefix + "lastSeen", 0);
            sim.isActive = prefs.getBoolean(prefix + "isActive", false);

            if (sim.iccid != null) {
                sims.add(sim);
            }
        }

        return sims;
    }

    private String maskICCID(String iccid) {
        if (iccid == null || iccid.length() < 8) return iccid;
        return iccid.substring(0, 4) + "***" + iccid.substring(iccid.length() - 4);
    }
}