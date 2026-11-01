package com.jellystar.iwlan;

import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.Settings;
import android.telephony.AccessNetworkConstants;
import android.telephony.AccessNetworkConstants.AccessNetworkType;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.data.ApnSetting;
import android.telephony.data.QualifiedNetworksService;
import android.telephony.ims.ImsMmTelManager;
import android.util.Log;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import java.util.List;

/**
 * Chooses between LTE and Wi-Fi for IMS, following the Wi-Fi Calling preference. Stock takes this
 * from the modem through MtkRIL, which AOSP does not have. With cellular preferred, Wi-Fi is used
 * only when the radio is off or cellular has been gone for the grace period.
 */
public class WlanQualifiedNetworksService extends QualifiedNetworksService {
    private static final String TAG = "JellyIwlanQns";
    private static final int EVENT_SERVICE_STATE_CHANGED = 1;
    private static final long CELLULAR_LOSS_GRACE_MS = 30_000;

    private class Provider extends NetworkAvailabilityProvider {
        private final HandlerThread mThread = new HandlerThread(TAG);
        private final Handler mHandler;
        private final ConnectivityManager mConnectivity = getSystemService(ConnectivityManager.class);
        private boolean mWifiConnected;
        private boolean mCellularInService;
        private int[] mLast;
        private Phone mPhone;
        private boolean mRadioOff;
        private boolean mGraceOver;
        private boolean mCellularDownPending;
        private boolean mStarted;

        private final ContentObserver mWfcObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                mHandler.post(() -> evaluate("wfc setting"));
            }
        };

        private final ConnectivityManager.NetworkCallback mWifiCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        mHandler.post(() -> {
                            mWifiConnected = true;
                            evaluate("wifi up");
                        });
                    }

                    @Override
                    public void onLost(Network network) {
                        mHandler.post(() -> {
                            mWifiConnected = false;
                            evaluate("wifi lost");
                        });
                    }
                };

        private final Runnable mGraceExpired = () -> {
            mGraceOver = true;
            evaluate("grace over");
        };

        Provider(int slotIndex) {
            super(slotIndex);
            mThread.start();
            mHandler = new Handler(mThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (msg.what == EVENT_SERVICE_STATE_CHANGED) {
                        readServiceState();
                    }
                }
            };
            mConnectivity.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(), mWifiCallback);
            getContentResolver().registerContentObserver(
                    SubscriptionManager.WFC_ENABLED_CONTENT_URI, true, mWfcObserver);
            mHandler.post(() -> {
                mPhone = PhoneFactory.getPhone(slotIndex);
                mPhone.registerForServiceStateChanged(mHandler, EVENT_SERVICE_STATE_CHANGED, null);
                readServiceState();
            });
        }

        private void readServiceState() {
            ServiceState state = mPhone.getServiceState();
            NetworkRegistrationInfo info = state.getNetworkRegistrationInfo(
                    NetworkRegistrationInfo.DOMAIN_PS, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
            mRadioOff = state.getState() == ServiceState.STATE_POWER_OFF;
            boolean inService = info != null && info.isInService();
            if (inService) {
                mHandler.removeCallbacks(mGraceExpired);
                mGraceOver = false;
            } else if (!mCellularDownPending && mCellularInService || !mStarted) {
                mHandler.removeCallbacks(mGraceExpired);
                mHandler.postDelayed(mGraceExpired, CELLULAR_LOSS_GRACE_MS);
            }
            mCellularDownPending = !inService;
            mStarted = true;
            mCellularInService = inService;
            evaluate("service state");
        }

        private void evaluate(String reason) {
            int[] networks = chooseNetworks();
            if (java.util.Arrays.equals(networks, mLast)) {
                return;
            }
            Log.i(TAG, "slot " + getSlotIndex() + " " + reason + ": wifi=" + mWifiConnected
                    + " cellular=" + mCellularInService + " radioOff=" + mRadioOff
                    + " ims=" + java.util.Arrays.toString(networks));
            mLast = networks;
            updateQualifiedNetworkTypes(ApnSetting.TYPE_IMS, List.of(java.util.Arrays.stream(networks)
                    .boxed().toArray(Integer[]::new)));
        }

        private int[] chooseNetworks() {
            if (!mWifiConnected || !wfcEnabled()) {
                return new int[] {AccessNetworkType.EUTRAN};
            }
            int mode = wfcMode();
            if (mode == ImsMmTelManager.WIFI_MODE_WIFI_ONLY) {
                return new int[] {AccessNetworkType.IWLAN};
            }
            boolean cellularGone = airplaneMode() || mRadioOff
                    || (!mCellularInService && mGraceOver);
            return cellularGone || mode == ImsMmTelManager.WIFI_MODE_WIFI_PREFERRED
                    ? new int[] {AccessNetworkType.IWLAN, AccessNetworkType.EUTRAN}
                    : new int[] {AccessNetworkType.EUTRAN, AccessNetworkType.IWLAN};
        }

        private boolean airplaneMode() {
            return Settings.Global.getInt(getContentResolver(),
                    Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
        }

        private ImsMmTelManager mmTel() {
            int subId = SubscriptionManager.getSubscriptionId(getSlotIndex());
            return SubscriptionManager.isValidSubscriptionId(subId)
                    ? ImsMmTelManager.createForSubscriptionId(subId) : null;
        }

        private boolean wfcEnabled() {
            try {
                ImsMmTelManager manager = mmTel();
                return manager != null && manager.isVoWiFiSettingEnabled();
            } catch (RuntimeException e) {
                Log.w(TAG, "reading the Wi-Fi Calling setting failed", e);
                return false;
            }
        }

        private int wfcMode() {
            try {
                ImsMmTelManager manager = mmTel();
                return manager == null ? ImsMmTelManager.WIFI_MODE_UNKNOWN
                        : manager.getVoWiFiModeSetting();
            } catch (RuntimeException e) {
                return ImsMmTelManager.WIFI_MODE_UNKNOWN;
            }
        }

        @Override
        public void close() {
            mConnectivity.unregisterNetworkCallback(mWifiCallback);
            getContentResolver().unregisterContentObserver(mWfcObserver);
            mHandler.removeCallbacksAndMessages(null);
            if (mPhone != null) {
                mPhone.unregisterForServiceStateChanged(mHandler);
            }
            mThread.quitSafely();
        }
    }

    @Override
    public NetworkAvailabilityProvider onCreateNetworkAvailabilityProvider(int slotIndex) {
        Log.i(TAG, "created for slot " + slotIndex);
        return new Provider(slotIndex);
    }
}
