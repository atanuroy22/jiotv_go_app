package com.skylake.skytv.jgorunner.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.skylake.skytv.jgorunner.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;

public class BinaryService extends Service {

    private static final String CHANNEL_ID = "BinaryServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STOP_BINARY = "com.skylake.sky7t.jgo.action.STOP_BINARY";
    public static final String ACTION_BINARY_STOPPED = "com.skylake.sky7t.jgo.action.BINARY_STOPPED";

    private Uri binaryUri;
    private String[] arguments;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_STOP_BINARY.equals(intent.getAction())) {
            BinaryExecutor.stopBinary();
            Log.d("BinaryService", "Binary stopped by user.");

            Intent broadcastIntent = new Intent(ACTION_BINARY_STOPPED);
            broadcastIntent.setPackage(getPackageName()); // Ensure the broadcast is internal-only
            sendBroadcast(broadcastIntent);


            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        binaryUri = Uri.parse(intent.getStringExtra("binaryUri"));
        arguments = intent.getStringArrayExtra("arguments");

        if (binaryUri == null) {
            binaryUri = Uri.parse("android.resource://com.skylake.skytv.jgorunner/raw/majorbin");
        }

        if (arguments == null) {
            arguments = new String[]{"Boot Start"};
        }

        if (binaryUri != null) {
            if (binaryUri.toString().startsWith("android.resource://")) {
                Log.d("BinaryService1", "BinaryService started in the background. ? "+ Arrays.toString(arguments));
                BinaryExecutor.executeBinary(this, arguments, output -> {
                    Log.d("BinaryOutput", output);
                });
            } else {
                Log.d("BinaryService2", "BinaryService started in the background. ? "+ Arrays.toString(arguments));
                BinaryExecutor.executeBinary(this, arguments, output -> {
                    Log.d("BinaryOutput", output);
                });
            }
        } else {
            Log.e("BinaryService", "No binary selected");
        }

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    private void handleDefaultBinary() {
        File binaryFile = new File(getFilesDir(), "defaultBinary"); // Changed file name to defaultBinary
        if (!binaryFile.exists()) {
            try (InputStream in = getResources().openRawResource(R.raw.majorbin);
                 FileOutputStream out = new FileOutputStream(binaryFile)) {

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }

                binaryFile.setExecutable(true, false);

            } catch (Exception e) {
                Log.e("BinaryService", "Failed to handle default binary", e);
            }
        }

        BinaryExecutor.executeBinary(this, arguments, output -> {
            Log.d("BinaryOutput", output);
        });
    }




    private Notification createNotification() {
        // Create the Stop Binary intent and PendingIntent
        Intent stopIntent = new Intent(this, BinaryService.class);
        stopIntent.setAction(ACTION_STOP_BINARY);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification;

        // Check for Android O and above for notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("JGO Service Running")
                    .setContentText("The server is running in the background.")
                    .setSmallIcon(R.drawable.notifications_24px)
                    .setOngoing(true)
                    .addAction(R.drawable.cancel_24px, "Stop Server", stopPendingIntent);
            notification = notificationBuilder.build();
        } else {
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
                    .setContentTitle("JGO Service Running")
                    .setContentText("The server is running in the background.")
                    .setSmallIcon(R.drawable.notifications_24px)
                    .setOngoing(true)
                    .addAction(R.drawable.cancel_24px, "Stop Server", stopPendingIntent);
            notification = notificationBuilder.build();
        }

        return notification;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_ID,
                        "Binary Service Channel",
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}