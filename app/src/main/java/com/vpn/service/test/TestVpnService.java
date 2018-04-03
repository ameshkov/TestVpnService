package com.vpn.service.test;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class TestVpnService extends VpnService implements Runnable {

    public static final String START_ACTION = "start";
    public static final String STOP_ACTION = "stop";

    private static final String TAG = TestVpnService.class.getName();
    private static final Object OBJECT = new Object();

    private static NetworkInfo prevNetworkInfo;

    private boolean started = false;
    private Thread thread;

    private ParcelFileDescriptor parcelFileDescriptor;
    private NetworkStateReceiver networkStateReceiver;
    private FileInputStream fileInputStream;

    @Override
    public void onCreate() {
        super.onCreate();

        networkStateReceiver = new NetworkStateReceiver();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);

        registerReceiver(networkStateReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (networkStateReceiver != null) {
            unregisterReceiver(networkStateReceiver);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (START_ACTION.equalsIgnoreCase(action)) {
                startVpn();
            }

            if (STOP_ACTION.equalsIgnoreCase(action)) {
                stopVpn();
            }
        }

        return Service.START_NOT_STICKY;
    }

    private void startVpn() {
        synchronized (OBJECT) {
            if (started) {
                Log.d(TAG, "Vpn service is already started");
                return;
            }

            started = true;
            prevNetworkInfo = getNetworkInfo(getApplicationContext());
            thread = new Thread(this, "TestVpnService-thread");
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void stopVpn() {
        synchronized (OBJECT) {
            if (!started) {
                Log.d(TAG, "Vpn service has been stopped already");
                return;
            }

            started = false;

            stopForeground(true);

            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }

                if (parcelFileDescriptor != null) {
                    parcelFileDescriptor.close();
                }
            } catch (IOException e) {
                // do nothing
            }

            if (thread != null) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    if (thread.isAlive()) {
                        thread.interrupt();
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        Builder builder = new Builder();
        builder.setSession("TestVpnService");
        builder.addAddress("172.168.123.123", 32);

        builder.addRoute("10.0.0.0", 8);
        builder.addRoute("100.64.0.0", 10);
        builder.addRoute("172.16.0.0", 12);
        builder.addRoute("192.0.0.0", 24);
        builder.addRoute("192.168.0.0", 16);
        builder.addRoute("224.0.0.0", 4);
        builder.addRoute("240.0.0.0", 4);
        builder.addRoute("255.255.255.255", 32);

        Intent localIntent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, localIntent, 0);
        builder.setConfigureIntent(pendingIntent);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            Network activeNetwork = getNetwork();
            builder.setUnderlyingNetworks(prevNetworkInfo == null ? null : new Network[]{activeNetwork});
        }

        showNotification("TestVpnService", "Service started");

        try {
            parcelFileDescriptor = builder.establish();
            fileInputStream = new FileInputStream(parcelFileDescriptor.getFileDescriptor());

            byte[] buffer = new byte[512];
            while (started) {
                int read = fileInputStream.read(buffer, 0, 512);
                if (read > 0) {
                    Log.d(TAG, "Read " + read + " bytes from the tunnel");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error build VPN service", e);
            startVpn();
        }
    }

    private Network getNetwork() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return connectivityManager.getActiveNetwork();
        }

        return null;
    }

    private void showNotification(String title, String text) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);

        Notification notification = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 1, intent, 0))
                .build();

        startForeground(notification.hashCode(), notification);
    }

    private NetworkInfo getNetworkInfo(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            return connectivityManager.getActiveNetworkInfo();
        }

        return null;
    }

    private class NetworkStateReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equalsIgnoreCase(action)) {
                if (hasConnectionChanged(context)) {
                    Log.d(TAG, "Connection change, restarts the vpn service");
                    Toast.makeText(context, "Connection change, restarts the vpn service", Toast.LENGTH_SHORT).show();
                    stopVpn();
                    startVpn();
                }
            }
        }

        private boolean hasConnectionChanged(Context context) {
            NetworkInfo networkInfo = getNetworkInfo(context);
            if (prevNetworkInfo == null) {
                prevNetworkInfo = networkInfo;
            }

            String prevHashInfo = getHashInfo(prevNetworkInfo);
            String hashInfo = getHashInfo(networkInfo);
            return !prevHashInfo.equalsIgnoreCase(hashInfo);
        }

        private String getHashInfo(NetworkInfo info) {
            if (info == null) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append(info.isConnected() ? 1 : 0);
            sb.append(info.getType());
            sb.append(info.getExtraInfo());

            return sb.toString();
        }
    }
}
