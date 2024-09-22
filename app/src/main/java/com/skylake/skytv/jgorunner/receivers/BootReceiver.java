package com.skylake.skytv.jgorunner.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import com.skylake.skytv.jgorunner.activity.WebPlayerActivity;
import com.skylake.skytv.jgorunner.services.BinaryService;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.net.HttpURLConnection;
import java.net.URL;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SkySharedPref preferenceManager = new SkySharedPref(context);

            String isAutoboot = preferenceManager.getKey("isFlagSetForAutoStartOnBoot");
            String isAutobootIPTV = preferenceManager.getKey("isFlagSetForAutoBootIPTV");

            if (isAutoboot != null && isAutoboot.equals("Yes")) {
                Toast.makeText(context, "[JGO] Running Server in Background", Toast.LENGTH_SHORT).show();
                Intent serviceIntent = new Intent(context, BinaryService.class);
                serviceIntent.putExtra("binaryUri", "android.resource://com.skylake.skytv.jgorunner/raw/majorbin");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(new Intent(serviceIntent));
                }
                Log.d("BootReceiver", "BinaryService started in the background.");

//                if (isAutobootIPTV != null && isAutobootIPTV.equals("Yes")) {
//                    executor.execute(() -> {
//                        try {
//                            boolean isServerRunning = checkServerStatus();
//
//                            if (isServerRunning) {
//                                String appPackageName = preferenceManager.getKey("app_packagename");
//                                if (!appPackageName.isEmpty()) {
//                                    Log.d("DIX", appPackageName);
//                                    String appLaunchActivity = preferenceManager.getKey("app_launch_activity");
//                                    String appName = preferenceManager.getKey("app_name");
//
//                                    PackageManager packageManager = context.getPackageManager();
//
//                                    if ("webtv".equals(appPackageName)) {
//                                        Intent launchIntent = new Intent(context, WebPlayerActivity.class);
//                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                                        context.startActivity(launchIntent);
//                                        Toast.makeText(context, "Opening IPTV", Toast.LENGTH_SHORT).show();
//                                        Log.d("DIX", "Opening IPTV");
//                                    } else if (!appPackageName.isEmpty() && !appName.isEmpty()) {
//                                        Intent launchIntent = packageManager.getLaunchIntentForPackage(appPackageName);
//                                        if (launchIntent != null) {
//                                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                                            context.startActivity(launchIntent);
//                                            Toast.makeText(context, "Opening " + appName, Toast.LENGTH_SHORT).show();
//                                            Log.d("DIX", "Opening " + appName);
//                                        } else {
//                                            Toast.makeText(context, "Cannot find the specified application", Toast.LENGTH_SHORT).show();
//                                            Log.d("DIX", "Cannot find the specified application");
//                                        }
//                                    } else {
//                                        Toast.makeText(context, "No application details found", Toast.LENGTH_SHORT).show();
//                                        Log.d("DIX", "No application details found");
//                                    }
//                                } else {
//                                    Toast.makeText(context, "IPTV not set.", Toast.LENGTH_SHORT).show();
//                                    Log.d("DIX", "IPTV not set");
//                                }
//                            } else {
//                                Toast.makeText(context, "Server issue. If it persists, restart.", Toast.LENGTH_SHORT).show();
//                                Log.d("DIX", "Server issue. If it persists, restart");
//                            }
//                        } catch (Exception e) {
//                            Log.e("DIX", "Error starting IPTV", e);
//                            Toast.makeText(context, "Error starting IPTV", Toast.LENGTH_SHORT).show();
//                            Log.d("DIX", "Error starting IPTV: " + e.getMessage());
//                        }
//
//                    });
//                }
            }
        }
    }

    // Placeholder method for checking server status
    public boolean checkServerStatus() {
        try {
            URL url = new URL("http://localhost:5350");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // 5 seconds timeout
            connection.setReadTimeout(5000); // 5 seconds timeout
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            Log.e("DIX", "Error checking server status", e);
            return false;
        }
    }
}
