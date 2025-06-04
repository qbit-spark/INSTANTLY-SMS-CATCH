package com.qbitspark.sms_catch;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {

    private static final String TAG = "ApiClient";
    //private static final String API_ENDPOINT = "http://192.168.1.4:8080/messages";
    private static final String API_ENDPOINT = "https://onepostz.xyz/api/callback/message";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    public static void sendMessage(final Context context, final MessageData messageData) {
        try {

            // Use receiver number as branch ID (instead of manual branch ID)
            String branchId = messageData.getReceiver();

            // Fallback to saved branch ID if receiver is null/empty
            if (branchId == null || branchId.isEmpty() || branchId.equals("Unknown")) {
                SharedPreferences sharedPreferences = context.getSharedPreferences("AppPrefs", MODE_PRIVATE);
                branchId = sharedPreferences.getString("BRANCH_ID", "DEFAULT");
            }

            JSONObject jsonPayload = getJsonObject(context, messageData, branchId);

            RequestBody body = RequestBody.create(jsonPayload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_ENDPOINT)
                    .post(body)
                    .build();

            Log.d(TAG, "Sending message: " + jsonPayload.toString());

            // Make the API call
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send message: " + e.getMessage());
                    // Leave in database for sync worker to try later
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response)  {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Message sent successfully: " + messageData.getId());

                        // Delete from database after successful send
                        new Thread(() -> {
                            MessageDatabase database = MessageDatabase.getInstance(context);
                            database.messageDao().deleteMessage(messageData.getId());
                        }).start();
                    } else {
                        Log.e(TAG, "API error: " + response.code() + " - " + response.message());
                        // Leave in database for sync worker to try later
                    }
                }

            });

        } catch (JSONException e) {
            Log.e(TAG, "JSON error: " + e.getMessage());
        }
    }

    @NonNull
    private static JSONObject getJsonObject(Context context, MessageData messageData, String branchId) throws JSONException {
        DeviceDetailsCollector deviceDetailsCollector = new DeviceDetailsCollector(context);

        // Create JSON payload
        JSONObject jsonPayload = new JSONObject();
        jsonPayload.put("branchId", branchId);  // Now using receiver number as branch ID
        jsonPayload.put("sender", messageData.getSender());
        jsonPayload.put("receiver", messageData.getReceiver());  // Add receiver to payload
        jsonPayload.put("message", messageData.getMessageBody());

        // Current time in ISO 8601 format
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String currentTimestamp = sdf.format(new Date()); // Gets current time

        jsonPayload.put("timestamp", currentTimestamp);

        jsonPayload.put("deviceDetails", deviceDetailsCollector.getAllDeviceDetailsJson());
        return jsonPayload;
    }
}