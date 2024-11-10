package com.skylake.skytv.jgorunner.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.skylake.skytv.jgorunner.services.BinaryService;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";
    private static final String AUTO_BOOT_PREF_KEY = "isFlagSetForAutoStartOnBoot";
    private static final String AUTO_BOOT_ENABLED = "Yes";
    private static final String AUTO_BOOT_STATE_PREF_KEY = "isFlagSetForAutoBootIPTV";
    private static final String AUTO_BOOT_STATE = "Yes";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            handleBootCompleted(context);
        }
    }

    private void handleBootCompleted(Context context) {
        SkySharedPref preferenceManager = new SkySharedPref(context);
        String isAutoboot = preferenceManager.getKey(AUTO_BOOT_PREF_KEY);
        String stateBG = preferenceManager.getKey(AUTO_BOOT_STATE_PREF_KEY);

        if (AUTO_BOOT_ENABLED.equals(isAutoboot)) {
            if (AUTO_BOOT_STATE.equals(stateBG)) {
                startBinaryService(context);
            } else {
                preferenceManager.setKey("isFlagSetForAutoStartServer","Yes");
                startBinaryServiceFG(context);
            }
        }
    }

    private void startBinaryService(Context context) {
        Toast.makeText(context, "[JGO] Running Server in Background", Toast.LENGTH_SHORT).show();
        Intent serviceIntent = new Intent(context, BinaryService.class);
        serviceIntent.putExtra("binaryUri", "android.resource://com.skylake.skytv.jgorunner/raw/majorbin");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        Log.d(TAG, "BinaryService started in the background.");
    }

    private void startBinaryServiceFG(Context context) {
        Toast.makeText(context, "[JGO] Running Server in Foreground", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "BinaryService started in the foreground.");
        launchMainActivity(context);
    }

    private void launchMainActivity(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            Toast.makeText(context, "[JGO] App launched in the foreground.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "App launched in the foreground.");
        } else {
            Toast.makeText(context, "[JGO] Unable to launch app: Launch intent is null.", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch app: Launch intent is null.");
        }
    }
}
