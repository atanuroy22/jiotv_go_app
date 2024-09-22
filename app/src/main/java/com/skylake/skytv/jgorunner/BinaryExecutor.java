package com.skylake.skytv.jgorunner;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class BinaryExecutor {

    private static Process binaryProcess = null;

    public interface OutputCallback {
        void onOutput(String output);
    }

    public static void executeBinary(final Context context, final String[] arguments, final OutputCallback callback) {
        new Thread(() -> {
            File binaryFile;
            try {
                // Define the file name and path in app storage
                String fileName = "majorbin";
                binaryFile = new File(context.getFilesDir(), fileName);

                // Check if the file already exists in app storage
                if (!binaryFile.exists()) {
                    // File does not exist, copy from res/raw
                    try (InputStream in = context.getResources().openRawResource(R.raw.majorbin);
                         FileOutputStream out = new FileOutputStream(binaryFile)) {

                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                }

                // Set executable permissions on the binary file
                binaryFile.setExecutable(true, false); // true = only owner, false = all users

                // Create the shell command to run the binary
                StringBuilder commandBuilder = new StringBuilder();
                commandBuilder.append(binaryFile.getAbsolutePath());

                // Append arguments to the command
                for (String arg : arguments) {
                    commandBuilder.append(" ").append(arg);
                }

                // Execute the command via shell
                binaryProcess = Runtime.getRuntime().exec(new String[] { "sh", "-c", commandBuilder.toString() });

                // Create a thread to read process output asynchronously
                new Thread(new StreamGobbler(binaryProcess.getInputStream(), callback)).start();

                // Keep the process running
                binaryProcess.waitFor(); // This call will block until the process terminates

            } catch (Exception e) {
                e.printStackTrace();
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
                    callback.onOutput(output); // Send the output back to the callback
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
