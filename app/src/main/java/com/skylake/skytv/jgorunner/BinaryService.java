package com.skylake.skytv.jgorunner;

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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class BinaryService extends Service {

    private static final String CHANNEL_ID = "BinaryServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_STOP_BINARY = "com.skylake.sky7t.jgo.action.STOP_BINARY";
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
            // Stop the binary process
            BinaryExecutor.stopBinary();
            Log.d("BinaryService", "Binary stopped by user.");

            // Send broadcast to notify MainActivity
            Intent broadcastIntent = new Intent(ACTION_BINARY_STOPPED);
            sendBroadcast(broadcastIntent);

            stopForeground(true); // Remove notification
            stopSelf(); // Stop the service
            return START_NOT_STICKY; // Stop the service immediately
        }

        binaryUri = Uri.parse(intent.getStringExtra("binaryUri"));
        arguments = intent.getStringArrayExtra("arguments");

        // Use default binary if none is selected
        if (binaryUri == null) {
            binaryUri = Uri.parse("android.resource://com.skylake.skytv.jgorunner/raw/majorbin");
        }

        if (binaryUri != null) {
            // Handle the binary file and execute it
            if (binaryUri.toString().startsWith("android.resource://")) {
                //handleDefaultBinary();
                // Execute the binary
                BinaryExecutor.executeBinary(this, arguments, output -> {
                    Log.d("BinaryOutput", output);
                    // Handle output here if necessary
                });
            } else {
                BinaryExecutor.executeBinary(this, arguments, output -> {
                    Log.d("BinaryOutput", output);
                    // Handle output here if necessary
                });
            }
        } else {
            Log.e("BinaryService", "No binary selected");
        }

        // Create the notification with a Stop button
        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY; // Keeps the service running
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

                binaryFile.setExecutable(true, false); // Set executable permissions

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("BinaryService", "Failed to handle default binary", e);
            }
        }

        // Execute the binary
        BinaryExecutor.executeBinary(this, arguments, output -> {
            Log.d("BinaryOutput", output);
            // Handle output here if necessary
        });
    }




    private Notification createNotification() {
        // Create the Stop Binary intent and PendingIntent
        Intent stopIntent = new Intent(this, BinaryService.class);
        stopIntent.setAction(ACTION_STOP_BINARY);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder notificationBuilder = null; // Add Stop button
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("JGO Service Running")
                    .setContentText("The server is running in the background.")
                    .setSmallIcon(R.drawable.notifications_24px) // Replace with your icon
                    .setOngoing(true) // Makes the notification non-clearable
                    .addAction(R.drawable.cancel_24px, "Stop Server", stopPendingIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return notificationBuilder.build();
        } else {
            return notificationBuilder.getNotification();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Handle service cleanup if needed
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not bound
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
