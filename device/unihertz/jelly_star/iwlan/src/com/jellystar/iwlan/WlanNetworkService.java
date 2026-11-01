package com.jellystar.iwlan;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.telephony.AccessNetworkConstants;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.NetworkService;
import android.telephony.NetworkServiceCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.List;

/** Reports the Wi-Fi transport as registered while Wi-Fi is connected. */
public class WlanNetworkService extends NetworkService {
    private static final String TAG = "JellyIwlanNet";

    private class Provider extends NetworkServiceProvider {
        private final ConnectivityManager mConnectivity = getSystemService(ConnectivityManager.class);
        private volatile boolean mWifiConnected;

        private final ConnectivityManager.NetworkCallback mCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        update(true);
                    }

                    @Override
                    public void onLost(Network network) {
                        update(false);
                    }
                };

        Provider(int slotIndex) {
            super(slotIndex);
            mConnectivity.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(), mCallback);
        }

        private void update(boolean connected) {
            Log.i(TAG, "slot " + getSlotIndex() + " wifi connected=" + connected);
            mWifiConnected = connected;
            notifyNetworkRegistrationInfoChanged();
        }

        @Override
        public void requestNetworkRegistrationInfo(int domain, NetworkServiceCallback callback) {
            if (domain != NetworkRegistrationInfo.DOMAIN_PS) {
                callback.onRequestNetworkRegistrationInfoComplete(
                        NetworkServiceCallback.RESULT_ERROR_UNSUPPORTED, null);
                return;
            }
            boolean connected = mWifiConnected;
            NetworkRegistrationInfo info = new NetworkRegistrationInfo.Builder()
                    .setDomain(domain)
                    .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                    .setRegistrationState(connected
                            ? NetworkRegistrationInfo.REGISTRATION_STATE_HOME
                            : NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_OR_SEARCHING)
                    .setAccessNetworkTechnology(connected
                            ? TelephonyManager.NETWORK_TYPE_IWLAN
                            : TelephonyManager.NETWORK_TYPE_UNKNOWN)
                    .setAvailableServices(connected
                            ? List.of(NetworkRegistrationInfo.SERVICE_TYPE_DATA)
                            : List.of())
                    .build();
            callback.onRequestNetworkRegistrationInfoComplete(
                    NetworkServiceCallback.RESULT_SUCCESS, info);
        }

        @Override
        public void close() {
            mConnectivity.unregisterNetworkCallback(mCallback);
        }
    }

    @Override
    public NetworkServiceProvider onCreateNetworkServiceProvider(int slotIndex) {
        Log.i(TAG, "created for slot " + slotIndex);
        return new Provider(slotIndex);
    }
}
