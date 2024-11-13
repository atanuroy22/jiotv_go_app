package com.skylake.skytv.jgorunner.services;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.skylake.skytv.jgorunner.R;
import com.skylake.skytv.jgorunner.data.SkySharedPref;
import com.skylake.skytv.jgorunner.utils.RemoteBinaryFetcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

public class BinaryExecutor {

    private static Process binaryProcess = null;
    private static StringBuilder outputBuilder = new StringBuilder();
    private static final String TAG = "BinaryExecutor";

    public interface OutputCallback {
        void onOutput(String output);
    }

    public static void executeBinary(final Context context, final String[] arguments, final OutputCallback callback) {
        new Thread(() -> {
            SkySharedPref preferenceManager = new SkySharedPref(context);
            File binaryFile = new File(context.getFilesDir(), "majorbin");

            try {
                String arch =  preferenceManager.getKey("ARCHx");
                Log.d(TAG, "Device architecture: " + arch);

                assert arch != null;
                if (!arch.toLowerCase().contains("arm") && !arch.toLowerCase().contains("aarch")) {
                    Toast.makeText(context, "The device architecture["+arch+"] is not supported.", Toast.LENGTH_SHORT).show();
                }

                handleBinaryFile(preferenceManager, binaryFile, context, callback);
                setBinaryExecutable(binaryFile);
                String command = buildCommand(preferenceManager, arguments, binaryFile,context);

                String command_log = preferenceManager.getKey("__MasterArgs_final");
                Log.d("DIX.Runner",command_log);

                binaryProcess = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
                new Thread(new StreamGobbler(binaryProcess.getInputStream(), callback)).start();

                binaryProcess.waitFor();
            } catch (Exception e) {
                Log.e(TAG, "Error executing binary: ", e);
                callback.onOutput("Exception occurred: " + e.getMessage());
            }
        }).start();
    }

    private static void handleBinaryFile(SkySharedPref preferenceManager, File binaryFile, Context context, OutputCallback callback) {
        if (shouldResetBinary(preferenceManager, binaryFile)) {
            deleteBinaryFile(binaryFile, preferenceManager);
        }

        if (!binaryFile.exists()) {
            RemoteBinaryFetcher.INSTANCE.startDownloadAndSave(context, callback);
            /* DEBUG CASE */
            boolean skipper = false;
            if (skipper) {
                copyBinaryFromResources(binaryFile, context);
            } else {
                /* 未来 USE CASE */
            }
        }
    }


    private static boolean shouldResetBinary(SkySharedPref preferenceManager, File binaryFile) {
        String resetBinaryCheck = preferenceManager.getKey("ResetBinaryCheck");
        return "Yes".equals(resetBinaryCheck) && binaryFile.exists();
    }

    private static void deleteBinaryFile(File binaryFile, SkySharedPref preferenceManager) {
        if (binaryFile.delete()) {
            Log.e(TAG, "Binary file deleted successfully.");
            preferenceManager.setKey("ResetBinaryCheck", "No");
        } else {
            Log.e(TAG, "Failed to delete binary file.");
        }
    }

    private static void copyBinaryFromResources(File binaryFile, Context context) {
        try (InputStream in = context.getResources().openRawResource(R.raw.majorbin);
             FileOutputStream out = new FileOutputStream(binaryFile)) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying binary from resources: ", e);
        }
    }

    private static void setBinaryExecutable(File binaryFile) {
        if (binaryFile.exists()) {
            boolean success = binaryFile.setExecutable(true, false);
            // Intentionally ignore the result without further action
            Log.d(TAG,"binaryPermissions: " + success);
        }
    }

    private static String buildCommand(SkySharedPref preferenceManager, String[] arguments, File binaryFile, Context context) {
        StringBuilder commandBuilder = new StringBuilder(binaryFile.getAbsolutePath());
        String __Port = preferenceManager.getKey("__Port");
        String __Public = preferenceManager.getKey("__Public");

        /* DEBUG CASE */
//        commandBuilder.append(" run").append(__Port).append(__Public).append(" --config \"jiotv-config.json\"");

        // Internal storage
        if (false) {
            commandBuilder.append(" run")
                    .append(__Port)
                    .append(__Public)
                    .append(" --config \"")
                    .append(context.getFilesDir().getAbsolutePath())
                    .append("/jiotv-config.json\"");
        }

        // External storage
        if (true) {
            String externalStoragePath = Environment.getExternalStorageDirectory().getPath();
            File folder = new File(externalStoragePath, ".jiotv_go");
            File jsonFile = new File(folder, "jiotv-config.json");
            File keyFile = new File(folder, "key.pem");
            File certFile = new File(folder, "cert.pem");

            commandBuilder.append(" run")
                    .append(__Port)
                    .append(__Public)
                    .append(" --config \"")
                    .append(jsonFile.getAbsolutePath())
                    .append("\"");

            /* DEBUG CASE: DRM */
            if (false){
                commandBuilder.append(" run")
                        .append(__Port)
                        .append(__Public)
                        .append(" --tls")
                        .append(" --tls-key \"")
                        .append(keyFile.getAbsolutePath())
                        .append("\"")
                        .append(" --tls-cert \"")
                        .append(certFile.getAbsolutePath())
                        .append("\"")
                        .append(" --config \"")
                        .append(jsonFile.getAbsolutePath())
                        .append("\"");
            }
        }



        preferenceManager.setKey("__MasterArgs_final",commandBuilder.toString());

//        for (String arg : arguments) {
//            commandBuilder.append(" ").append(arg);
//        }

        return commandBuilder.toString();
    }
//
//    public static void stopBinary() {
//        if (binaryProcess != null) {
//            binaryProcess.destroy();
//            binaryProcess = null;
//        }
//    }

    public static void stopBinary() {
        if (binaryProcess != null) {
            try {
                // Close all streams before killing the process
                closeProcessStreams(binaryProcess);

                // Retrieve the process ID (PID)
                String pid = getProcessId(binaryProcess);
                if (pid != null) {
                    Log.i(TAG, "Killing process tree for PID: " + pid);

                    // Execute shell command to kill the entire process tree
                    killProcessTree(pid);
                }

                // First attempt to stop the process gracefully
                binaryProcess.destroy();
                Log.i(TAG+"DIX", "Process destroyed.");

                // If needed, escalate to forcibly destroying it
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    binaryProcess.destroyForcibly();
                }
                Log.i(TAG, "Process forcibly destroyed.");

            } catch (Exception e) {
                Log.e(TAG, "Error while stopping binary process: ", e);
            } finally {
                binaryProcess = null;  // Reset the process variable
            }
        }
    }

    // Helper method to retrieve the process ID (PID) via reflection
    private static String getProcessId(Process process) {
        try {
            // Use reflection to access the PID field in the Process class
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            Log.e(TAG, "String.valueOf(pidField.getInt(process)");
            return String.valueOf(pidField.getInt(process));
        } catch (Exception e) {
            Log.e(TAG, "Error getting process ID: ", e);
            return null;
        }
    }

    // Helper method to kill the process tree using a shell command
    private static void killProcessTree(String pid) {
        try {
            // Execute shell command to kill the process tree (including subprocesses)
            String[] cmd = { "sh", "-c", "kill -9 " + pid };
            Process killProcess = Runtime.getRuntime().exec(cmd);
            killProcess.waitFor();  // Wait for the kill command to finish
            Log.i(TAG, "Successfully killed process tree for PID: " + pid);
        } catch (Exception e) {
            Log.e(TAG, "Error killing process tree: ", e);
        }
    }

    // Helper method to close process streams
    private static void closeProcessStreams(Process process) {
        try {
            process.getInputStream().close();
            process.getOutputStream().close();
            process.getErrorStream().close();
        } catch (Exception e) {
            Log.e(TAG, "Error closing process streams: ", e);
        }
    }



    public static boolean isBinaryRunning() {
        return binaryProcess != null;
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final OutputCallback callback;

        public StreamGobbler(InputStream inputStream, OutputCallback callback) {
            this.inputStream = inputStream;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    String output = new String(buffer, 0, bytesRead);
                    outputBuilder.append(output).append("\n");
                    Log.d(TAG, "Process output: " + output);
                    callback.onOutput(output);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading binary output: ", e);
            }
        }
    }
}

