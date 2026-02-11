package com.skylake.skytv.jgorunner.activities;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.skylake.skytv.jgorunner.R;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ExoplayerActivity extends ComponentActivity {

    private static final String TAG = "ExoplayerActivity-DIX";
    private static final String TAG_MPD = "MPD_Scraper-DIX";
    private static final String DEFAULT_VIDEO_URL = "http://localhost:5350/live/143.m3u8";
    private static final int HIDE_CONTROLS_DELAY_MS = 1000;

    private ExoPlayer player;
    private PlayerView playerView;
    private boolean isInPipMode = false;
    private String currentPlayId;
    private String activePlaybackUrl;
    private boolean didFallbackToHls = false;

    private SkySharedPref skyPref;
    private String filterQX;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen and immersive mode
        setImmersiveMode();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_web_player_exo);

        playerView = findViewById(R.id.player_view);

        skyPref = SkySharedPref.getInstance(this);
        filterQX = skyPref.getMyPrefs().getFilterQX();

        // Intent handling
        Intent intent = getIntent();
        String videoUrl = intent.getStringExtra("video_url");
        String signatureFallback = intent.getStringExtra("zone");
        signatureFallback = (signatureFallback == null || signatureFallback.isEmpty()) ? "0x0" : signatureFallback;
        currentPlayId = intent.getStringExtra("current_play_id");
        String[] channelNumbersArray = intent.getStringArrayExtra("channels_list");
        List<String> channelNumbers = channelNumbersArray != null ? Arrays.asList(channelNumbersArray) : null;

        if (videoUrl == null || videoUrl.isEmpty()) {
            videoUrl = DEFAULT_VIDEO_URL;
        }

        String formattedUrl = formatVideoUrl(videoUrl, signatureFallback);
        Log.d(TAG, "Formatted URL: " + formattedUrl);

        didFallbackToHls = false;
        setupPlayer(formattedUrl);

        hideControlsAfterDelay();

    }

    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private void setImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @OptIn(markerClass = UnstableApi.class)
    private void setupPlayer(String videoUrl) {
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);
        DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                if (didFallbackToHls) return;
                String current = activePlaybackUrl == null ? "" : activePlaybackUrl;
                String cleaned = current.toLowerCase().split("\\?")[0].split("#")[0];
                if (!cleaned.endsWith(".mpd") && !cleaned.contains(".mpd")) return;

                String fallback = swapExtensionToM3u8(current);
                if (fallback.equals(current)) return;

                didFallbackToHls = true;
                Log.d(TAG, "DASH failed, fallback to HLS: " + fallback);
                playUrl(fallback);
            }
        });

        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        playUrl(videoUrl);
    }

    private String formatVideoUrl(String videoUrl, String signatureFallback) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return DEFAULT_VIDEO_URL;
        }

        // Replace the "/live/" segment and adjust based on quality
        if (videoUrl.contains("q=low")) {
            videoUrl = videoUrl.replace("/live/", "/live/low/");
        } else if (videoUrl.contains("q=high")) {
            videoUrl = videoUrl.replace("/live/", "/live/high/");
        } else if (videoUrl.contains("q=medium")) {
            videoUrl = videoUrl.replace("/live/", "/live/medium/");
        }

        if ("TV".equals(signatureFallback) && !"auto".equals(filterQX)) {
            Log.d(TAG, "Exo- TV- Redirect-");
            switch (filterQX) {
                case "low" -> videoUrl = videoUrl.replace("/live/", "/live/low/");
                case "high" -> videoUrl = videoUrl.replace("/live/", "/live/high/");
                case "medium" -> videoUrl = videoUrl.replace("/live/", "/live/medium/");
            }
        }

        try {
            Uri u = Uri.parse(videoUrl);
            String path = u.getPath();
            if (path != null) {
                Matcher matcher = Pattern.compile(".*/(play|player)/(\\d+)$").matcher(path);
                if (matcher.matches()) {
                    String id = matcher.group(2);
                    videoUrl = u.buildUpon().path("/live/" + id + ".mpd").clearQuery().build().toString();
                }
            }
        } catch (Exception ignored) {
        }

        if (videoUrl.contains(".m3u8") || videoUrl.contains(".mpd")) {
            int questionMarkIndex = videoUrl.indexOf("?");
            if (questionMarkIndex != -1) {
                videoUrl = videoUrl.substring(0, questionMarkIndex);
            }
        }

        // Fix potential issue with "//.m3u8"
        videoUrl = videoUrl.replace("//.m3u8", ".m3u8");
        videoUrl = videoUrl.replace("//.mpd", ".mpd");

        return videoUrl;
    }

    private void playUrl(String url) {
        activePlaybackUrl = url;
        String mimeType = inferMimeType(url);

        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.parse(url));
        if (mimeType != null && !mimeType.isEmpty()) builder.setMimeType(mimeType);
        player.setMediaItem(builder.build());
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private String inferMimeType(String url) {
        if (url == null) return null;
        String cleaned = url.toLowerCase().split("\\?")[0].split("#")[0];
        if (cleaned.endsWith(".mpd") || cleaned.contains(".mpd")) return MimeTypes.APPLICATION_MPD;
        if (cleaned.endsWith(".m3u8") || cleaned.contains(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    private String swapExtensionToM3u8(String url) {
        if (url == null) return "";
        String base = url;
        String suffix = "";
        int hash = base.indexOf('#');
        if (hash >= 0) {
            suffix = base.substring(hash) + suffix;
            base = base.substring(0, hash);
        }
        int q = base.indexOf('?');
        if (q >= 0) {
            suffix = base.substring(q) + suffix;
            base = base.substring(0, q);
        }
        base = base.replaceAll("(?i)\\.(mpd)$", "");
        base = base.replaceAll("(?i)\\.(m3u8)$", "");
        base = base.replaceAll("(?i)\\.(m3u)$", "");
        return base + ".m3u8" + suffix;
    }

    @OptIn(markerClass = UnstableApi.class)
    private void hideControlsAfterDelay() {
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> playerView.hideController(),
                HIDE_CONTROLS_DELAY_MS
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!isInPipMode) {
            createPIPMode();
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        finish();
        startActivity(intent);
    }

    private void createPIPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Rational aspectRatio = new Rational(16, 9);
            PictureInPictureParams.Builder pipBuilder = new PictureInPictureParams.Builder();
            pipBuilder.setAspectRatio(aspectRatio);

            enterPictureInPictureMode(pipBuilder.build());
            isInPipMode = true;
        } else {
            Toast.makeText(this, "PiP mode is not supported on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiPMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPiPMode, newConfig);
        isInPipMode = isInPiPMode;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setImmersiveMode();
        }
    }


    // Optional: Only enable if needed for MPD scraping
    private void scrapeAndLogUrls(String mpdUrl) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(mpdUrl).build();

                try (Response response = client.newCall(request).execute()) {
                    String mpdContent = response.body().string();
                    Log.d(TAG_MPD, "MPD URL: " + mpdUrl);
                    Log.d(TAG_MPD, "MPD Content: " + mpdContent);

                    String playUrl = extractPlayUrl(mpdContent);
                    String licenseUrl = extractLicenseUrl(mpdContent);

                    playUrl = playUrl.replace("\\/", "http://localhost:5350/").replace("\\u0026", "&");
                    licenseUrl = licenseUrl.replace("\\/", "http://localhost:5350/").replace("\\u0026", "&");

                    Log.d(TAG_MPD, "Play URL: " + playUrl);
                    Log.d(TAG_MPD, "License URL: " + licenseUrl);
                }
            } catch (IOException e) {
                Log.e(TAG_MPD, "Error scraping MPD: " + e.getMessage());
            }
        }).start();
    }

    private String extractPlayUrl(String mpdContent) {
        Pattern pattern = Pattern.compile("player\\.load\\(\"([^\"]*)\"\\)");
        Matcher matcher = pattern.matcher(mpdContent);
        return matcher.find() ? matcher.group(1) : "Not Found";
    }

    private String extractLicenseUrl(String mpdContent) {
        Pattern pattern = Pattern.compile("com\\.widevine\\.alpha\":\\s*\"([^\"]*)");
        Matcher matcher = pattern.matcher(mpdContent);
        return matcher.find() ? matcher.group(1) : "Not Found";
    }
}
