package com.skylake.skytv.jgorunner.services;

import android.content.Context;
import android.util.Log;

import com.skylake.skytv.jgorunner.R;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class BinaryExecutor {

    private static Process binaryProcess = null;
    private static StringBuilder outputBuilder = new StringBuilder();

    public interface OutputCallback {
        void onOutput(String output);
    }

    public static void executeBinary(final Context context, final String[] arguments, final OutputCallback callback) {
        new Thread(() -> {
            File binaryFile;
            try {
                String fileName = "majorbin";
                binaryFile = new File(context.getFilesDir(), fileName);

                if (!binaryFile.exists()) {
                    try (InputStream in = context.getResources().openRawResource(R.raw.majorbin);
                         FileOutputStream out = new FileOutputStream(binaryFile)) {

                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }

                binaryFile.setExecutable(true, false); // true = only owner, false = all users

                StringBuilder commandBuilder = new StringBuilder();
                commandBuilder.append(binaryFile.getAbsolutePath());

                SkySharedPref preferenceManager = new SkySharedPref(context);
                String __MasterArgs = preferenceManager.getKey("__MasterArgs");
                String __Port = preferenceManager.getKey("__Port");
                String __Public = preferenceManager.getKey("__Public");
                String __EPG = preferenceManager.getKey("__EPG");


                commandBuilder.append(" run").append(__Port).append(__Public);

                //commandBuilder.append(" run").append(__EPG).append(__Port).append(__Public);

                preferenceManager.setKey("__MasterArgs", commandBuilder.toString() );


//                Append arguments to the command
//                commandBuilder.append(" run --port 5350 --public");

//                // Append arguments to the command
//                for (String arg : arguments) {
//                    commandBuilder.append(" ").append(arg);
//                }

                binaryProcess = Runtime.getRuntime().exec(new String[] { "sh", "-c", commandBuilder.toString() });

                new Thread(new StreamGobbler(binaryProcess.getInputStream(), callback)).start();

                binaryProcess.waitFor();

            } catch (Exception e) {
                Log.d("BinaryExecutor", String.valueOf(e));
                callback.onOutput("Exception occurred: " + e.getMessage());
            }
        }).start();
    }

    public static void stopBinary() {
        if (binaryProcess != null) {
            binaryProcess.destroy();
            binaryProcess = null;
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
                    callback.onOutput(output);
                }
            } catch (Exception e) {
                Log.d("BinaryExecutor", String.valueOf(e));
            }
        }
    }
}
