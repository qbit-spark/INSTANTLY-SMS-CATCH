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
}