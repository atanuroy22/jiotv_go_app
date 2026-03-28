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
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.skylake.skytv.jgorunner.R;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        // Pre-buffer more data so normal network hiccups don't stall playback.
        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        /* minBufferMs                     */ 20_000,
                        /* maxBufferMs                     */ 60_000,
                        /* bufferForPlaybackMs             */ 2_500,
                        /* bufferForPlaybackAfterRebufferMs */ 6_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        player = new ExoPlayer.Builder(this).setLoadControl(loadControl).build();
        playerView.setPlayer(player);
        // Keep the last rendered frame frozen on screen during buffering and
        // retries — prevents the surface going black on network hiccups.
        playerView.setKeepContentOnPlayerReset(true);

        // Short timeouts: if a segment fetch hangs, fail fast (8 s) and retry
        // instead of waiting the OS default ~15 s with a black screen.
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
                .setAllowCrossProtocolRedirects(true);
        // Live offset targeting: stay 15 s behind the live edge so the next
        // 15 s of segments are always pre-fetched before they're needed.
        // Any network hiccup shorter than 15 s is absorbed with zero stall.
        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(videoUrl))
                .setMimeType("application/x-mpegURL")
                .setLiveConfiguration(
                        new MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(15_000)
                                .setMinOffsetMs(8_000)
                                .setMaxOffsetMs(25_000)
                                .setMinPlaybackSpeed(0.97f)
                                .setMaxPlaybackSpeed(1.03f)
                                .build()
                )
                .build();
        MediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem);

        player.setMediaSource(hlsMediaSource);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private String formatVideoUrl(String videoUrl, String signatureFallback) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return DEFAULT_VIDEO_URL;
        }

        Uri parsed = Uri.parse(videoUrl);
        String path = parsed.getPath();
        if (path == null) {
            path = "";
        }

        String qFromUrl = parsed.getQueryParameter("q");
        String effectiveQuality = qFromUrl;

        if ("TV".equals(signatureFallback) && !"auto".equals(filterQX)) {
            Log.d(TAG, "Exo- TV- Redirect-");
            if (filterQX != null && !filterQX.isEmpty()) {
                effectiveQuality = filterQX;
            }
        }

        String normalizedPath = path
                .replace("/live/low/", "/live/")
                .replace("/live/medium/", "/live/")
                .replace("/live/high/", "/live/");

        if (effectiveQuality != null) {
            switch (effectiveQuality.toLowerCase()) {
                case "low":
                    normalizedPath = normalizedPath.replace("/live/", "/live/low/");
                    break;
                case "high":
                    normalizedPath = normalizedPath.replace("/live/", "/live/high/");
                    break;
                case "medium":
                    normalizedPath = normalizedPath.replace("/live/", "/live/medium/");
                    break;
                default:
                    break;
            }
        }

        if (normalizedPath.contains("/live/") &&
                !normalizedPath.endsWith(".m3u8") &&
                !normalizedPath.endsWith(".m3u")) {
            normalizedPath = normalizedPath.endsWith("/")
                    ? normalizedPath.substring(0, normalizedPath.length() - 1) + ".m3u8"
                    : normalizedPath + ".m3u8";
        }

        Uri.Builder builder = parsed.buildUpon().encodedPath(normalizedPath).clearQuery();

        Map<String, List<String>> queryMap = new LinkedHashMap<>();
        for (String name : parsed.getQueryParameterNames()) {
            if ("q".equalsIgnoreCase(name)) {
                continue;
            }
            queryMap.put(name, parsed.getQueryParameters(name));
        }

        for (Map.Entry<String, List<String>> entry : queryMap.entrySet()) {
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                builder.appendQueryParameter(entry.getKey(), null);
            } else {
                for (String value : values) {
                    builder.appendQueryParameter(entry.getKey(), value);
                }
            }
        }

        String formatted = builder.build().toString()
                .replace(".m3u8.m3u8", ".m3u8")
                .replace("//.m3u8", ".m3u8");

        return formatted;
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
