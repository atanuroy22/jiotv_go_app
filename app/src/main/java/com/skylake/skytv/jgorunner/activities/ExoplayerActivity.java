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
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.skylake.skytv.jgorunner.R;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExoplayerActivity extends ComponentActivity {

    private ExoPlayer player;
    private PlayerView playerView;
    private boolean isInPipMode = false;
    private String current_play_id;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_web_player_exo);

        playerView = findViewById(R.id.player_view);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        Intent intent = getIntent();
        String videoUrl = intent.getStringExtra("video_url");
        current_play_id = intent.getStringExtra("current_play_id");
        String[] channelNumbersArray = getIntent().getStringArrayExtra("channels_list");
        List<String> channelNumbers = channelNumbersArray != null ? Arrays.asList(channelNumbersArray) : null;
        // For future usage


        assert videoUrl != null;
        if (videoUrl.isEmpty()) {
            videoUrl = "http://localhost:5350/live/143.m3u8";
        }

        String formattedUrl = formatVideoUrl(videoUrl);

        Log.d("DIX", formattedUrl);

        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();

        MediaSource hlsMediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(true)
                .createMediaSource(MediaItem.fromUri(Uri.parse(formattedUrl)));

        player.setMediaSource(hlsMediaSource);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setShowNextButton(false);
        playerView.setShowPreviousButton(false);
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
        player.prepare();
        player.setPlayWhenReady(true);

        hideControlsAfterDelay();

        videoUrl = videoUrl.replace(".m3u8", "");
        videoUrl = videoUrl.replace("live", "mpd");

//        scrapeAndLogUrls(videoUrl);

    }

    private String formatVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            return null;
        }

        // Replace the "/live/" segment and adjust based on quality
        if (videoUrl.contains("q=low")) {
            videoUrl = videoUrl.replace("/live/", "/live/low/");
        } else if (videoUrl.contains("q=high")) {
            videoUrl = videoUrl.replace("/live/", "/live/high/");
        } else if (videoUrl.contains("q=medium")) {
            videoUrl = videoUrl.replace("/live/", "/live/medium/");
        }

        // Remove query parameters if URL contains ".m3u8" *Intentionally removed .m3u8
        if (videoUrl.contains(".m3u8")) {
            int questionMarkIndex = videoUrl.indexOf("?");
            if (questionMarkIndex != -1) {
                videoUrl = videoUrl.substring(0, questionMarkIndex);
            }
        }

        // Fix potential issue with "//.m3u8"
        videoUrl = videoUrl.replace("//.m3u8", ".m3u8");

        return videoUrl;
    }


    @OptIn(markerClass = UnstableApi.class)
    private void hideControlsAfterDelay() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> playerView.hideController(), 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) {
            player.setPlayWhenReady(true);
            player.getPlaybackState();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.stop();
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
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }


    private void scrapeAndLogUrls(String mpdUrl) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(mpdUrl)
                        .build();

                Response response = client.newCall(request).execute();
                assert response.body() != null;
                String mpdContent = response.body().string();
                Log.d("MPD_Scraper", "MPD URL: " + mpdUrl);

                Log.d("MPD_Scraper", ": " + mpdContent);

                String playUrl = extractPlayUrl(mpdContent);

                String licenseUrl = extractLicenseUrl(mpdContent);

                playUrl = playUrl.replace("\\/", "http://localhost:5350/");
                playUrl = playUrl.replace("\\u0026", "&");

                licenseUrl = licenseUrl.replace("\\/", "http://localhost:5350/");
                licenseUrl = licenseUrl.replace("\\u0026", "&");


                Log.d("MPD_Scraper", "Play URL: " + playUrl);
                Log.d("MPD_Scraper", "License URL: " + licenseUrl);

            } catch (IOException e) {
                Log.e("MPD_Scraper", "Error scraping MPD: " + e.getMessage());
            }
        }).start();
    }

    private String extractPlayUrl(String mpdContent) {
        Pattern pattern = Pattern.compile("player\\.load\\(\"([^\"]*)\"\\)");
        Matcher matcher = pattern.matcher(mpdContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Not Found";
    }

    private String extractLicenseUrl(String mpdContent) {
        Pattern pattern = Pattern.compile("com\\.widevine\\.alpha\":\\s*\"([^\"]*)");
        Matcher matcher = pattern.matcher(mpdContent);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "Not Found";
    }


}
