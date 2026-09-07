package demo.ads;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;

import androidx.annotation.NonNull;

public class NetworkManager {

    private static OnMonitorListener listener;
    private static ConnectivityManager connectivityManager;
    private static ConnectivityManager.NetworkCallback networkCallback;

    private static final NetworkRequest networkRequest = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build();

    public interface OnMonitorListener {
        void onConnectivityChanged(boolean isConnected);
    }

    public static void Monitoring(Context context, OnMonitorListener monitorListener) {
        stopMonitoring();
        listener = monitorListener;
        if (context == null) return;

        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager = appContext.getSystemService(ConnectivityManager.class);
        } else {
            connectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        if (connectivityManager == null) return;

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (listener != null) {
                    listener.onConnectivityChanged(true);
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (listener != null) {
                    listener.onConnectivityChanged(false);
                }
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities);
            }
        };
        try {
            connectivityManager.requestNetwork(networkRequest, networkCallback);
        } catch (Exception ignored) {}
    }

    public static void stopMonitoring() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        networkCallback = null;
        connectivityManager = null;
        listener = null;
    }
}
