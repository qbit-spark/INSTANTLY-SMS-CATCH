package com.qbitspark.sms_catch;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 101;

    // All required permissions
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
    };

    private String currentSimBeingConfigured = null; // Track which SIM we're configuring

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NetworkChangeReceiver networkChangeReceiver = new NetworkChangeReceiver(this);
        networkChangeReceiver.registerNetworkCallback();
        Log.i(TAG,"Network callback registered");

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        EditText branchIdInput = findViewById(R.id.branchIdInput);
        Button saveBranchButton = findViewById(R.id.saveBranchButton);
        TextView statusTextView = findViewById(R.id.statusTextView);

        // Set up input field for phone numbers
        setupPhoneNumberInput(branchIdInput);

        saveBranchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = branchIdInput.getText().toString().trim();

                if (phoneNumber != null && !phoneNumber.isEmpty()) {

                    if (currentSimBeingConfigured != null) {
                        // Save for the specific SIM being configured
                        SharedPreferences simPrefs = getSharedPreferences("SIM_PHONE_NUMBERS", MODE_PRIVATE);
                        SharedPreferences.Editor simEditor = simPrefs.edit();
                        simEditor.putString(currentSimBeingConfigured, phoneNumber);
                        simEditor.apply();

                        Toast.makeText(MainActivity.this,
                                "✅ Saved " + phoneNumber + " for " + currentSimBeingConfigured,
                                Toast.LENGTH_LONG).show();

                        // Clear input for next SIM
                        branchIdInput.setText("");
                        currentSimBeingConfigured = null;

                        // IMMEDIATELY check for next unconfigured SIM
                        updateDisplay();

                    } else {
                        Toast.makeText(MainActivity.this, "Error: No SIM selected", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    Toast.makeText(MainActivity.this,
                            "❌ Phone number is required! Cannot skip SIM configuration.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        // Check and request all required permissions
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, SMS_PERMISSION_CODE);
        } else {
            startSmsListener();
        }

        // Trigger an immediate sync when the app starts
        WorkManagerHelper.triggerImmediateSync(this);
        // Schedule periodic sync worker
        WorkManagerHelper.scheduleSyncWorker(this);
    }

    /**
     * Sets up the phone number input field with proper formatting and validation
     */
    private void setupPhoneNumberInput(EditText phoneInput) {
        // Set input type to phone number
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        // Set maximum length to 10 characters
        phoneInput.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(10),
                // Only allow digits
                new android.text.InputFilter() {
                    @Override
                    public CharSequence filter(CharSequence source, int start, int end,
                                               android.text.Spanned dest, int dstart, int dend) {
                        if (source.toString().matches("[0-9]*")) {
                            return null; // Accept the input
                        }
                        return ""; // Reject the input
                    }
                }
        });

        // Add text watcher for real-time validation feedback
        phoneInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();

                // Real-time feedback
                if (text.length() > 0 && !text.startsWith("0")) {
                    phoneInput.setError("Must start with 0");
                } else if (text.length() == 10 && isValidPhoneNumberSilent(text)) {
                    phoneInput.setError(null);
                    // Show checkmark or positive feedback
                } else {
                    phoneInput.setError(null);
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    /**
     * Silent validation (no toast messages) for real-time feedback
     */
    private boolean isValidPhoneNumberSilent(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) return false;
        phoneNumber = phoneNumber.replaceAll("[\\s-]", "");

        return phoneNumber.matches("\\d{10}") &&
                phoneNumber.startsWith("0") &&
                !phoneNumber.equals("0000000000") &&
                !phoneNumber.equals("0123456789") &&
                !phoneNumber.matches("0(\\d)\\1{8}");
    }

    /**
     * Validates phone number format: must start with 0 and be exactly 10 digits
     * @param phoneNumber The phone number to validate
     * @return true if valid, false if invalid (with error message shown)
     */
    private boolean isValidPhoneNumber(String phoneNumber) {
        // Check if empty
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(this,
                    "❌ Phone number is required! Cannot skip SIM configuration.",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // Remove any spaces or dashes that user might have entered
        phoneNumber = phoneNumber.replaceAll("[\\s-]", "");

        // Check if it contains only digits
        if (!phoneNumber.matches("\\d+")) {
            Toast.makeText(this,
                    "❌ Invalid format! Phone number must contain only digits.\n" +
                            "Example: 0745051250",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // Check if it starts with 0
        if (!phoneNumber.startsWith("0")) {
            Toast.makeText(this,
                    "❌ Invalid format! Phone number must start with 0.\n" +
                            "Example: 0745051250",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // Check if it's exactly 10 digits
        if (phoneNumber.length() != 10) {
            Toast.makeText(this,
                    "❌ Invalid length! Phone number must be exactly 10 digits.\n" +
                            "You entered: " + phoneNumber.length() + " digits\n" +
                            "Example: 0745051250",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        // Check for common invalid patterns
        if (phoneNumber.equals("0000000000") ||
                phoneNumber.equals("0123456789") ||
                phoneNumber.matches("0(\\d)\\1{8}")) { // All same digits after 0
            Toast.makeText(this,
                    "❌ Invalid number! Please enter a real phone number.\n" +
                            "Example: 0745051250",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private void updateDisplay() {
        TextView statusTextView = findViewById(R.id.statusTextView);
        EditText branchIdInput = findViewById(R.id.branchIdInput);
        Button saveBranchButton = findViewById(R.id.saveBranchButton);

        // Get all active SIMs and their configuration status
        SimConfigStatus configStatus = getSimConfigurationStatus();

        if (configStatus.hasUnconfiguredSims()) {
            // FORCE configuration of next unconfigured SIM - NO SKIPPING ALLOWED
            currentSimBeingConfigured = configStatus.getNextUnconfiguredSim();

            statusTextView.setVisibility(View.VISIBLE);
            branchIdInput.setVisibility(View.VISIBLE);
            saveBranchButton.setVisibility(View.VISIBLE);

            // Show current progress and force configuration
            String progressMessage = "📱 SIM CONFIGURATION REQUIRED\n\n" +
                    "Progress: " + configStatus.getConfiguredCount() + "/" + configStatus.getTotalCount() + " SIMs configured\n\n" +
                    "⚠️ ALL SIMs must be configured to continue\n" +
                    "Currently configuring: " + currentSimBeingConfigured + "\n\n";

            if (configStatus.getConfiguredCount() > 0) {
                progressMessage += "✅ Already configured:\n" + configStatus.getConfiguredSimsList() + "\n";
            }

            statusTextView.setText(progressMessage);
            branchIdInput.setHint("📞 Enter 10-digit number starting with 0 (e.g., 0745051250)");
            saveBranchButton.setText("💾 SAVE FOR " + currentSimBeingConfigured);

            // Set input type to numeric only
            branchIdInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

            // Make input field mandatory
            branchIdInput.setError(null);

        } else {
            // ALL SIMs are configured - show success status
            currentSimBeingConfigured = null;

            statusTextView.setVisibility(View.VISIBLE);
            branchIdInput.setVisibility(View.GONE);
            saveBranchButton.setVisibility(View.GONE);

            statusTextView.setText("🎉 ALL SIMs CONFIGURED SUCCESSFULLY!\n\n" +
                    "Everything run smoothly..\n\n" +
                    configStatus.getAllSimsStatusDisplay());
        }
    }

    // Helper class to track SIM configuration status
    private class SimConfigStatus {
        private int totalSims = 0;
        private int configuredSims = 0;
        private String nextUnconfiguredSim = null;
        private StringBuilder configuredList = new StringBuilder();
        private StringBuilder allSimsStatus = new StringBuilder();

        public boolean hasUnconfiguredSims() {
            return nextUnconfiguredSim != null;
        }

        public String getNextUnconfiguredSim() {
            return nextUnconfiguredSim;
        }

        public int getTotalCount() {
            return totalSims;
        }

        public int getConfiguredCount() {
            return configuredSims;
        }

        public String getConfiguredSimsList() {
            return configuredList.toString();
        }

        public String getAllSimsStatusDisplay() {
            return allSimsStatus.toString();
        }
    }

    private SimConfigStatus getSimConfigurationStatus() {
        SimConfigStatus status = new SimConfigStatus();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                SubscriptionManager subscriptionManager = SubscriptionManager.from(this);
                List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();

                if (subscriptions != null && !subscriptions.isEmpty()) {
                    SharedPreferences simPrefs = getSharedPreferences("SIM_PHONE_NUMBERS", MODE_PRIVATE);
                    status.totalSims = subscriptions.size();

                    for (SubscriptionInfo subInfo : subscriptions) {
                        String carrierName = subInfo.getCarrierName().toString();
                        int subId = subInfo.getSubscriptionId();
                        String simKey = carrierName + "_SIM_" + subId;

                        String savedNumber = simPrefs.getString(simKey, null);
                        if (savedNumber != null && !savedNumber.isEmpty()) {
                            // This SIM is configured
                            status.configuredSims++;
                            status.configuredList.append("   📱 ").append(simKey).append(": ").append(savedNumber).append("\n");
                            status.allSimsStatus.append("✅ ").append(simKey).append(": ").append(savedNumber).append("\n");
                            status.allSimsStatus.append("   Branch ID: ").append(savedNumber).append("_").append(simKey).append("\n\n");
                        } else {
                            // This SIM needs configuration
                            if (status.nextUnconfiguredSim == null) {
                                status.nextUnconfiguredSim = simKey; // First unconfigured SIM
                            }
                            status.allSimsStatus.append("❌ ").append(simKey).append(": NOT CONFIGURED\n\n");
                        }
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot access SIM information", e);
            }
        }

        return status;
    }

    private boolean hasAllPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                startSmsListener();
                updateDisplay();
            } else {
                Toast.makeText(this, "All permissions are required for this app to work",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSmsListener() {
        Intent serviceIntent = new Intent(this, SmsListenerService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @Override
    public void onBackPressed() {
        // Prevent user from leaving if SIMs are not configured
        SimConfigStatus configStatus = getSimConfigurationStatus();

        if (configStatus.hasUnconfiguredSims()) {
            Toast.makeText(this,
                    "⚠️ Cannot exit! Please configure all " + configStatus.getTotalCount() + " SIM cards first.\n" +
                            "Progress: " + configStatus.getConfiguredCount() + "/" + configStatus.getTotalCount() + " completed",
                    Toast.LENGTH_LONG).show();

            // Do NOT call super.onBackPressed() - prevent exit
            return;
        }

        // All SIMs configured, allow normal back behavior
        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (hasAllPermissions()) {
            updateDisplay();
        }
    }

    /**
     * Enhanced SIM configuration status using ICCID tracking
     */

    private SimConfigStatus getEnhancedSimConfigurationStatus() {
        SimConfigStatus status = new SimConfigStatus();
        EnhancedSIMManager simManager = new EnhancedSIMManager(this);

        // Detect any SIM changes first
        EnhancedSIMManager.SwapDetectionResult swapResult = simManager.detectSIMChanges();

        if (swapResult.hasChanges()) {
            Log.i("MainActivity", "SIM changes detected: " + swapResult.getChangesSummary());
            // Show user the changes
            showSIMChangesAlert(swapResult);
        }

        // Get current SIM status
        List<EnhancedSIMManager.SIMInfo> activeSIMs = swapResult.activeSIMs;
        status.totalSims = activeSIMs.size();

        for (EnhancedSIMManager.SIMInfo simInfo : activeSIMs) {
            if (simInfo.userPhoneNumber != null && !simInfo.userPhoneNumber.isEmpty()) {
                // This SIM is configured
                status.configuredSims++;
                status.configuredList.append("   📱 ").append(simInfo.getDisplayName()).append("\n");
                status.allSimsStatus.append("✅ ").append(simInfo.getDisplayName()).append("\n");
                status.allSimsStatus.append("   ICCID: ").append(maskICCID(simInfo.iccid)).append("\n");
                status.allSimsStatus.append("   Slot: ").append(simInfo.slotIndex).append("\n\n");
            } else {
                // This SIM needs configuration
                if (status.nextUnconfiguredSim == null) {
                    status.nextUnconfiguredSim = simInfo.carrierName + "_SLOT" + simInfo.slotIndex;
                    currentSimBeingConfigured = simInfo.iccid; // Store ICCID instead
                }
                status.allSimsStatus.append("❌ ").append(simInfo.carrierName)
                        .append(" (Slot ").append(simInfo.slotIndex).append("): NOT CONFIGURED\n");
                status.allSimsStatus.append("   ICCID: ").append(maskICCID(simInfo.iccid)).append("\n\n");
            }
        }

        return status;
    }

    /**
     * Enhanced save button click handler
     */
    private void handleEnhancedSave(String phoneNumber) {
        if (phoneNumber != null && !phoneNumber.isEmpty() && currentSimBeingConfigured != null) {

            // Save using ICCID as the key
            EnhancedSIMManager simManager = new EnhancedSIMManager(this);
            simManager.saveSIMConfiguration(currentSimBeingConfigured, phoneNumber);

            Toast.makeText(MainActivity.this,
                    "✅ Saved " + phoneNumber + " for SIM with ICCID: " + maskICCID(currentSimBeingConfigured),
                    Toast.LENGTH_LONG).show();

            // Clear input for next SIM
            EditText branchIdInput = findViewById(R.id.branchIdInput);
            branchIdInput.setText("");
            currentSimBeingConfigured = null;

            // Check for next unconfigured SIM
            updateEnhancedDisplay();

        } else {
            Toast.makeText(MainActivity.this,
                    "❌ Phone number is required! Cannot skip SIM configuration.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Enhanced display update with ICCID tracking
     */
    private void updateEnhancedDisplay() {
        TextView statusTextView = findViewById(R.id.statusTextView);
        EditText branchIdInput = findViewById(R.id.branchIdInput);
        Button saveBranchButton = findViewById(R.id.saveBranchButton);

        EnhancedSIMManager simManager = new EnhancedSIMManager(this);

        if (!simManager.areAllSIMsConfigured()) {
            // Get next unconfigured SIM
            EnhancedSIMManager.SIMInfo unconfiguredSIM = simManager.getNextUnconfiguredSIM();

            if (unconfiguredSIM != null) {
                currentSimBeingConfigured = unconfiguredSIM.iccid;

                statusTextView.setVisibility(View.VISIBLE);
                branchIdInput.setVisibility(View.VISIBLE);
                saveBranchButton.setVisibility(View.VISIBLE);

                String progressMessage = "📱 SIM CONFIGURATION REQUIRED\n\n" +
                        "⚠️ ALL SIMs must be configured to continue\n" +
                        "Currently configuring:\n" +
                        "📱 " + unconfiguredSIM.carrierName + " (Slot " + unconfiguredSIM.slotIndex + ")\n" +
                        "🆔 ICCID: " + maskICCID(unconfiguredSIM.iccid) + "\n\n";

                statusTextView.setText(progressMessage);
                branchIdInput.setHint("📞 Enter phone number for this SIM");
                saveBranchButton.setText("💾 SAVE FOR " + unconfiguredSIM.carrierName);
            }
        } else {
            // ALL SIMs are configured
            currentSimBeingConfigured = null;

            statusTextView.setVisibility(View.VISIBLE);
            branchIdInput.setVisibility(View.GONE);
            saveBranchButton.setVisibility(View.GONE);

            // Show comprehensive status
            EnhancedSIMManager.SwapDetectionResult currentStatus = simManager.detectSIMChanges();
            StringBuilder statusMessage = new StringBuilder("🎉 ALL SIMs CONFIGURED SUCCESSFULLY!\n\n");

            for (EnhancedSIMManager.SIMInfo sim : currentStatus.activeSIMs) {
                statusMessage.append("✅ ").append(sim.getDisplayName()).append("\n");
                statusMessage.append("   📍 Slot: ").append(sim.slotIndex).append("\n");
                statusMessage.append("   🆔 ICCID: ").append(maskICCID(sim.iccid)).append("\n\n");
            }

            statusTextView.setText(statusMessage.toString());
        }
    }

    /**
     * Show alert when SIM changes are detected
     */
    private void showSIMChangesAlert(EnhancedSIMManager.SwapDetectionResult swapResult) {
        if (swapResult.hasChanges()) {
            StringBuilder alertMessage = new StringBuilder("🔔 SIM CHANGES DETECTED!\n\n");
            alertMessage.append(swapResult.getChangesSummary());

            // You could show a dialog here instead of just logging
            Log.w("MainActivity", alertMessage.toString());

            // Example: Show toast notification
            Toast.makeText(this, "SIM configuration changed! Check logs for details.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Mask ICCID for display (show first 4 and last 4 digits)
     */
    private String maskICCID(String iccid) {
        if (iccid == null || iccid.length() < 8) return iccid;
        return iccid.substring(0, 4) + "***" + iccid.substring(iccid.length() - 4);
    }

    /**
     * Replace your existing onClick handler with this enhanced version
     */
    private void setupEnhancedSaveButton() {
        Button saveBranchButton = findViewById(R.id.saveBranchButton);
        EditText branchIdInput = findViewById(R.id.branchIdInput);

        saveBranchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = branchIdInput.getText().toString().trim();

                if (isValidPhoneNumber(phoneNumber)) {
                    handleEnhancedSave(phoneNumber);
                }
            }
        });
    }
}