package com.skylake.skytv.jgorunner.activity;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.skylake.skytv.jgorunner.R;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

public class WebPlayerActivity extends ComponentActivity {

    private static final String TAG = "WebPlayerActivity";
    private static final String PORT_PREF_KEY = "isCustomSetForPORT";
    private static final String DEFAULT_URL_TEMPLATE = "http://localhost:%d";

    private WebView webView;
    private ProgressBar loadingSpinner;
    private String url;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_player);

        int savedPortNumber = getSavedPortNumber();
        url = String.format(DEFAULT_URL_TEMPLATE, savedPortNumber);

        Log.d(TAG, "URL: " + url);

        setupBackPressedCallback();
        setupFullScreenMode();

        webView = findViewById(R.id.webview);
        loadingSpinner = findViewById(R.id.loading_spinner);

        setupWebView();
        loadUrl();
    }

    private int getSavedPortNumber() {
        SkySharedPref preferenceManager = new SkySharedPref(this);
        String portString = preferenceManager.getKey(PORT_PREF_KEY);
        return portString != null ? Integer.parseInt(portString) : 5350; // Default to 5350 if not set
    }

    private void setupBackPressedCallback() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }

    private void setupFullScreenMode() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        updateSystemUiVisibility();
    }

    private void updateSystemUiVisibility() {
        getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        webView.setWebViewClient(new CustomWebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setDefaultTextEncodingName("utf-8");
        webSettings.setMediaPlaybackRequiresUserGesture(false); // Allow autoplay
    }

    private void loadUrl() {
        if (url != null) {
            webView.loadUrl(url);
        }
    }

    private void setDarkTheme() {
        if (webView != null) {
            String jsCode = "document.getElementsByTagName('html')[0].setAttribute('data-theme', 'dark');" +
                    "localStorage.setItem('theme', 'dark');";
            webView.evaluateJavascript(jsCode, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.contains("/play/")) {
                String newUrl = url.replace("/play/", "/player/");
                webView.loadUrl(newUrl);
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            loadingSpinner.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            loadingSpinner.setVisibility(View.GONE);
            if (url.contains("/player/")) {
                Log.d(TAG, "Playing: " + url);
                setupFullScreenMode();
                playVideoInFullScreen(view);
            }
        }

        private void playVideoInFullScreen(WebView view) {
            view.loadUrl("javascript:(function() { " +
                    "var video = document.getElementsByTagName('video')[0]; " +
                    "if (video) { " +
                    "  video.style.width = '100vw'; " +  // Use viewport width
                    "  video.style.height = '100vh'; " + // Use viewport height
                    "  video.style.objectFit = 'contain'; " + // Scale the video while preserving aspect ratio
                    "  video.play(); " +
                    "} " +
                    "})()");
        }
    }
}
