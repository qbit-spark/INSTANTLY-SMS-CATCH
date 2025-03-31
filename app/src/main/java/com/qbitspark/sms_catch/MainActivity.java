package com.qbitspark.sms_catch;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PERMISSION_CODE = 101;

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

        saveBranchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String branchId = branchIdInput.getText().toString().trim();

                // Check if the input is not empty or null
                if (branchId != null && !branchId.isEmpty()) {
                    // Save to SharedPreferences
                    SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();

                    // This will create a new value or replace existing one with the same key
                    editor.putString("BRANCH_ID", branchId);
                    editor.apply();


                    // Optional: Show success message
                    Toast.makeText(MainActivity.this, "Branch ID saved successfully", Toast.LENGTH_SHORT).show();

                    // Optional: Clear the input field after saving
                    branchIdInput.setText("");

                    // Hide input and button, show status text
                    statusTextView.setVisibility(View.VISIBLE);
                    branchIdInput.setVisibility(View.GONE);
                    saveBranchButton.setVisibility(View.GONE);
                } else {
                    // Show error message if input is empty
                    Toast.makeText(MainActivity.this, "Please enter a valid Branch ID", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Check and request SMS read permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECEIVE_SMS},
                    SMS_PERMISSION_CODE);
        } else {
            startSmsListener();
        }

        // Trigger an immediate sync when the app starts
        WorkManagerHelper.triggerImmediateSync(this);
        // Schedule periodic sync worker
        WorkManagerHelper.scheduleSyncWorker(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSmsListener();
            } else {
                Toast.makeText(this, "SMS permission is required for this app",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startSmsListener() {
        // Start the SMS listener service
        Intent serviceIntent = new Intent(this, SmsListenerService.class);

        // For Android 8.0+ use startForegroundService
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onStart() {

        // Get references to views
        super.onStart();
        TextView statusTextView = findViewById(R.id.statusTextView);
        EditText branchIdInput = findViewById(R.id.branchIdInput);
        Button saveBranchButton = findViewById(R.id.saveBranchButton);

        // Check if branch ID exists in SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        String savedBranchId = sharedPreferences.getString("BRANCH_ID", null);

        if (savedBranchId != null && !savedBranchId.isEmpty()) {
            // Branch ID exists, show only the status text
            statusTextView.setVisibility(View.VISIBLE);
            branchIdInput.setVisibility(View.GONE);
            saveBranchButton.setVisibility(View.GONE);

            // Optional: Add the branch ID to the status message
            statusTextView.setText(getString(R.string.sms_listener_running_in_background) +
                    "\nBranch ID: " + savedBranchId);
        } else {
            // No Branch ID, show input and button
            statusTextView.setVisibility(View.GONE);
            branchIdInput.setVisibility(View.VISIBLE);
            saveBranchButton.setVisibility(View.VISIBLE);

            // Reset to default message
            statusTextView.setText(R.string.sms_listener_running_in_background);
        }
    }
}