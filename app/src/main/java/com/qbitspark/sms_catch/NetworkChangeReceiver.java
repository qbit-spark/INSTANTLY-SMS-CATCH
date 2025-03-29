package com.qbitspark.sms_catch;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

public class NetworkChangeReceiver {

    private static final String TAG = "NetworkChangeReceiver";
    private ConnectivityManager.NetworkCallback networkCallback;
    private final ConnectivityManager connectivityManager;
    private final Context context;

    public NetworkChangeReceiver(Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void registerNetworkCallback() {
        if (networkCallback == null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    Log.i(TAG, "Network available, triggering immediate sync");
                    WorkManagerHelper.triggerImmediateSync(context);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    Log.i(TAG, "Network disconnected");
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                    Log.d(TAG,"Network Capabilities Changed");
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        Log.i(TAG, "Network has internet capabilities, triggering immediate sync");
                        WorkManagerHelper.triggerImmediateSync(context);
                    }
                }
            };

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
        }
    }

    public void unregisterNetworkCallback() {
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }
    }
}