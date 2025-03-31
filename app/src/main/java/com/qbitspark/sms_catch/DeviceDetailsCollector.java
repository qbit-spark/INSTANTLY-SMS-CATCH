package com.qbitspark.sms_catch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.core.app.ActivityCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class DeviceDetailsCollector {
    private final Context context;

    public DeviceDetailsCollector(Context context) {
        this.context = context;
    }

    /**
     * Collects all device details and returns as a JSONObject
     */
    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    public JSONObject getAllDeviceDetailsJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("hardwareDetails", getHardwareDetailsJson());
        json.put("androidVersionDetails", getAndroidVersionDetailsJson());
        json.put("deviceIdentifiers", getDeviceIdentifiersJson());
        json.put("networkInformation", getNetworkInformationJson());
        json.put("batteryInformation", getBatteryInformationJson());

        return json;
    }

    /**
     * Collects hardware details and returns as a JSONObject
     */
    private JSONObject getHardwareDetailsJson() throws JSONException {
        JSONObject json = new JSONObject();

        json.put("model", Build.MODEL);
        json.put("manufacturer", Build.MANUFACTURER);
        json.put("device", Build.DEVICE);
        json.put("product", Build.PRODUCT);
        json.put("board", Build.BOARD);
        json.put("hardware", Build.HARDWARE);

        // Screen details
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        json.put("screenResolution", metrics.widthPixels + " x " + metrics.heightPixels);
        json.put("screenDensity", metrics.densityDpi);

        float widthInches = metrics.widthPixels / metrics.xdpi;
        float heightInches = metrics.heightPixels / metrics.ydpi;
        json.put("screenSize", String.format(Locale.US, "%.1f", Math.sqrt(widthInches * widthInches + heightInches * heightInches)));

        // RAM details
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        json.put("totalRam", formatSize(memoryInfo.totalMem));
        json.put("availableRam", formatSize(memoryInfo.availMem));

        // Storage details
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        StatFs statFs = new StatFs(externalStorageDirectory.getPath());

        json.put("totalStorage", formatSize(statFs.getBlockSizeLong() * statFs.getBlockCountLong()));
        json.put("availableStorage", formatSize(statFs.getBlockSizeLong() * statFs.getAvailableBlocksLong()));

        // Processor details
        json.put("cpuModel", Build.HARDWARE);
        json.put("processorCores", Runtime.getRuntime().availableProcessors());

        return json;
    }

    private JSONObject getAndroidVersionDetailsJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("androidVersion", Build.VERSION.RELEASE);
        json.put("apiLevel", Build.VERSION.SDK_INT);
        json.put("securityPatch", Build.VERSION.SECURITY_PATCH);
        json.put("kernelVersion", System.getProperty("os.version"));
        json.put("buildId", Build.ID);
        json.put("buildTime", Build.TIME);
        json.put("fingerprint", Build.FINGERPRINT);
        return json;
    }

    @RequiresPermission("android.permission.READ_PRIVILEGED_PHONE_STATE")
    private JSONObject getDeviceIdentifiersJson() throws JSONException {
        JSONObject json = new JSONObject();

        @SuppressLint("HardwareIds")
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        json.put("androidId", androidId);

        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            json.put("imei", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? telephonyManager.getImei() : telephonyManager.getDeviceId());
            json.put("serialNumber", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? Build.getSerial() : Build.SERIAL);
        } else {
            json.put("imei", "Permission not granted");
            json.put("serialNumber", "Permission not granted");
        }

        json.put("macAddress", getMacAddress());

        return json;
    }

    private JSONObject getNetworkInformationJson() throws JSONException {
        JSONObject json = new JSONObject();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

        json.put("connected", activeNetworkInfo != null && activeNetworkInfo.isConnected());
        json.put("connectionType", activeNetworkInfo != null ? activeNetworkInfo.getTypeName() : "Unknown");

        json.put("ipAddress", getIPAddress(true));

        return json;
    }

    private JSONObject getBatteryInformationJson() throws JSONException {
        JSONObject json = new JSONObject();
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            json.put("chargingStatus", status == BatteryManager.BATTERY_STATUS_CHARGING ? "Charging" :
                    status == BatteryManager.BATTERY_STATUS_DISCHARGING ? "Discharging" :
                            status == BatteryManager.BATTERY_STATUS_FULL ? "Full" :
                                    "Unknown");

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            json.put("batteryLevel", (level * 100 / (float) scale) + "%");

            json.put("batteryHealth", batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) == BatteryManager.BATTERY_HEALTH_GOOD ? "Good" : "Unknown");

            json.put("batteryTemperature", batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0 + "°C");
        }

        return json;
    }

    private String formatSize(long size) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double sizeAsDouble = size;
        while (sizeAsDouble >= 1024 && unitIndex < units.length - 1) {
            sizeAsDouble /= 1024;
            unitIndex++;
        }
        return String.format(Locale.US, "%.2f %s", sizeAsDouble, units[unitIndex]);
    }

    private String getMacAddress() {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getMacAddress();
        } catch (Exception e) {
            return "Not Available";
        }
    }

    private String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Not Available";
    }
}
