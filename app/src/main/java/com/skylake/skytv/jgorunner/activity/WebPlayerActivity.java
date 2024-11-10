package com.skylake.skytv.jgorunner.activity;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.skylake.skytv.jgorunner.R;
import com.skylake.skytv.jgorunner.data.SkySharedPref;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class WebPlayerActivity extends ComponentActivity {

    private static final String TAG = "WebPlayerActivity";
    private static final String PORT_PREF_KEY = "isCustomSetForPORT";
    private static final String DEFAULT_URL_TEMPLATE = "http://localhost:%d";

    private WebView webView;
    private ProgressBar loadingSpinner;
    private String url;

    private List<String> channelNumbers;
    private String initURL;

    private String currentPlayId;
    private String currentLogoUrl;
    private String currentChannelName;

    private static final String RECENT_CHANNELS_KEY = "recent_channels";
    private final List<Channel> recentChannels = new ArrayList<>();


    private static class Channel {
        String playId;
        String logoUrl;
        String channelName;

        Channel(String playId, String logoUrl, String channelName) {
            this.playId = playId;
            this.logoUrl = logoUrl;
            this.channelName = channelName;
        }
    }


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

    private int playerUrlCount = 0;

    private void setupBackPressedCallback() {
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null) {
                    String currentUrl = webView.getUrl();

                    if (currentUrl != null && currentUrl.contains("/player/")) {
                        playerUrlCount++;
                        if (playerUrlCount >= 3) {
                            webView.loadUrl(initURL);
                        } else {
                            webView.goBack();
                        }
                    } else if (webView.canGoBack()) {
                        playerUrlCount++;
                        if (playerUrlCount >= 6) {
                            finish();
                        } else {
                            webView.goBack();
                        }
                    } else {
                        finish();
                    }
                } else {
                    finish();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);
    }


    private void setupFullScreenMode() {
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        updateSystemUiVisibility();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateSystemUiVisibility();  // This maintains full-screen mode in both orientations
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

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && webView.getUrl() != null && webView.getUrl().contains("/player/")) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    navigateToNextChannel();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    navigateToPreviousChannel();
                    return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void navigateToNextChannel() {
        navigateChannel(1);
    }

    private void navigateToPreviousChannel() {
        navigateChannel(-1);
    }

    private void navigateChannel(int direction) {
        if (channelNumbers == null || channelNumbers.isEmpty()) {
            Log.d(TAG, "No channel numbers available.");
            return;
        }

        Log.d(TAG, "Total channels available: " + channelNumbers.size());

        String currentUrl = webView.getUrl();
        assert currentUrl != null;

        int queryIndex = currentUrl.indexOf('?');
        String currentNumber;

        if (queryIndex != -1) {
            currentNumber = currentUrl.substring(currentUrl.lastIndexOf('/') + 1, queryIndex);
        } else {
            currentNumber = currentUrl.substring(currentUrl.lastIndexOf('/') + 1);
        }

        int index = channelNumbers.indexOf(currentNumber);

        if (index >= 0) {
            int newIndex = (index + direction + channelNumbers.size()) % channelNumbers.size();
            String newNumber = channelNumbers.get(newIndex);

            String newUrl;
            if (queryIndex != -1) {
                newUrl = currentUrl.replace("/" + currentNumber + "?", "/" + newNumber + "?");
            } else {
                newUrl = currentUrl.replace("/" + currentNumber, "/" + newNumber);
            }

            Log.d(TAG, "Navigating to Channel: " + newUrl);
            webView.loadUrl(newUrl);
        } else {
            Log.d(TAG, "Current number not found in channel numbers.");
        }
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.contains("/play/")) {
                initURL = webView.getUrl();
                Log.d(TAG, "Saving initURL: " + initURL);

                // Extract the play ID from the URL
                //String playId = url.substring(url.lastIndexOf("/play/") + 6); // Extracting play ID

                String playId = url.matches(".*\\/play\\/([^\\/]+).*") ? url.replaceAll(".*\\/play\\/([^\\/]+).*", "$1") : null;


                Log.d("WB", playId);

                // Use JavaScript to extract the channel logo and name
                view.evaluateJavascript(
                        "(function() { " +
                                "try { " +
                                "    var channelCard = document.querySelector('a[href*=\"/play/" + playId + "\"]'); " +
                                "    if (channelCard) { " +
                                "        var logoElement = channelCard.querySelector('img'); " +
                                "        var nameElement = channelCard.querySelector('span'); " +
                                "        var logoUrl = logoElement ? logoElement.getAttribute('src') : null; " +
                                "        var channelName = nameElement ? nameElement.innerText : null; " +
                                "        return JSON.stringify({playId: '" + playId + "', logoUrl: logoUrl, channelName: channelName}); " +
                                "    } else { " +
                                "        return null; " +
                                "    } " +
                                "} catch (error) { " +
                                "    return null; " +
                                "} " +
                                "})();", result -> {
                            if (result != null && !result.equals("null")) {
                                try {
                                    // Remove any extra quotes surrounding the JSON result
                                    String jsonString = result.replaceAll("^\"|\"$", "").replace("\\\"", "\"");
                                    JSONObject jsonResult = new JSONObject(jsonString);
                                    currentPlayId = jsonResult.getString("playId");
                                    currentLogoUrl = jsonResult.getString("logoUrl");
                                    currentChannelName = jsonResult.getString("channelName");

                                    Log.d(TAG, "Channel Clicked: " + currentChannelName + " (Play ID: " + currentPlayId + ")");
                                    saveRecentChannel(currentPlayId, currentLogoUrl, currentChannelName);
                                } catch (JSONException e) {
                                    Log.d(TAG, "JSON parsing error: " + e.getMessage());
                                }
                            } else {
                                Log.d(TAG, "No channel data extracted.");
                            }
                        });


                String newUrl = url.replace("/play/", "/player/");
                Log.d(TAG, "Loading new player URL: " + newUrl);
                webView.loadUrl(newUrl);
                return true;
            } else if (!url.contains("/play/") && !url.contains("/player/")) {
                initURL = url;
                return false;
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
            } else{
                moveSearchInput(view);
                extractChannelNumbers();
                loadRecentChannels();
            }
        }

        private void extractChannelNumbers() {
            webView.evaluateJavascript("Array.from(document.querySelectorAll('.card')).map(card => card.getAttribute('href').match(/\\/play\\/(\\d+)/)[1])", result -> {
                if (result != null && !result.isEmpty()) {
                    result = result.replace("[", "").replace("]", "").replace("\"", "");
                    channelNumbers = Arrays.asList(result.split(","));
                    Log.d(TAG, "Channel Numbers: " + channelNumbers);
                }
            });
        }

        private void playVideoInFullScreen(WebView view) {
            int orientation = getResources().getConfiguration().orientation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                view.loadUrl("javascript:(function() { " +
                        "var video = document.getElementsByTagName('video')[0]; " +
                        "if (video) { " +
                        "  video.style.width = '100vw'; " +  // Use full viewport width
                        "  video.style.height = '100vh'; " + // Use full viewport height
                        "  video.style.objectFit = 'contain'; " + // Maintain aspect ratio, contain in view
                        "  video.play(); " +
                        "} " +
                        "})()");
            } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                view.loadUrl("javascript:(function() { " +
                        "var video = document.getElementsByTagName('video')[0]; " +
                        "if (video) { " +
                        "  video.style.width = '100vw'; " +  // Use full viewport width
                        "  video.style.height = 'auto'; " +  // Automatically adjust height based on width
                        "  video.style.objectFit = 'contain'; " + // Maintain aspect ratio
                        "  video.play(); " +
                        "} " +
                        "})()");
            }
        }


        private void moveSearchInput(WebView view) {
            view.loadUrl("javascript:(function() { " +
                    "var searchButton = document.getElementById('portexe-search-button'); " +
                    "var searchInput = document.getElementById('portexe-search-input'); " +
                    "if (searchButton && searchInput) { " +
                    "  searchButton.parentNode.insertBefore(searchInput, searchButton.nextSibling); " +
                    "} " +
                    "})()");
        }
    }

    private void injectTVChannel(String channelName, String playId, String logoUrl) {

        String jsCode = "javascript:(function() {" +
                "console.log('Starting channel injection process...');" +

                "var channelGrid = document.querySelector('.grid.grid-cols-2');" +
                "console.log('Attempting to find the channel grid:', channelGrid);" +

                "if (channelGrid) {" +
                "  console.log('Channel grid found:', channelGrid);" +
                "  var existingChannel = document.querySelector('a[href=\"/play/" + playId + "\"]');" +
                "  console.log('Checking for existing channel with playId:', '" + playId + "');" +

                "  if (existingChannel) {" +
                "    console.log('Channel with playId ' + '" + playId + "' + ' already exists, skipping injection.');" +
                "  } else {" +
                "    console.log('Channel does not exist. Proceeding with channel injection...');" +
                "    var newChannel = document.createElement('a');" +
                "    newChannel.href = '/play/" + playId + "';" +
                "    newChannel.className = 'card border-2 border-gold shadow-lg hover:shadow-xl hover:bg-base-300 transition-all duration-200 ease-in-out scale-100 hover:scale-105';" +
                "    var cardContent = `<div class=\"flex flex-col items-center p-2 sm:p-4\">" +
                "      <img src=\"" + logoUrl + "\" loading=\"lazy\" alt=\"" + channelName + "\" class=\"h-14 w-14 sm:h-16 sm:w-16 md:h-18 md:w-18 lg:h-20 lg:w-20 rounded-full bg-gray-200\" />" +
                "      <span class=\"text-lg font-bold mt-2\">" + channelName + "</span>" +
                "      <div class=\"absolute top-2 right-2\">" +
                "        <svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" fill=\"gold\" viewBox=\"0 -960 960 960\">" +
                "        <path d=\"m480-120-58-52q-101-91-167-157T150-447.5Q111-500 95.5-544T80-634q0-94 63-157t157-63q52 0 99 22t81 62q34-40 81-62t99-22q94 0 157 63t63 157q0 46-15.5 90T810-447.5Q771-395 705-329T538-172l-58 52Z\"/>  " +
                "        </svg>" +
                "      </div>" +
                "    </div>`;" +

                "    newChannel.innerHTML = cardContent;" +
                "    channelGrid.insertBefore(newChannel, channelGrid.firstChild);" +
                "    console.log('Successfully injected new channel:', newChannel);" +
                "  }" +
                "} else {" +
                "  console.log('Failed to find the channel grid. Injection skipped.');" +
                "}" +
                "})()";

        webView.evaluateJavascript(jsCode, null);
        Log.d("ChannelInjection", "JavaScript code injected into the WebView.");
    }









    private void saveRecentChannel(String playId, String logoUrl, String channelName) {
        SkySharedPref preferenceManager = new SkySharedPref(this);

        // Load existing recent channels from preferenceManager
        String recentChannelsJson = preferenceManager.getKey(RECENT_CHANNELS_KEY);
        if (recentChannelsJson != null && !recentChannelsJson.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(recentChannelsJson);
                recentChannels.clear();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    recentChannels.add(new Channel(
                            jsonObject.getString("playId"),
                            jsonObject.getString("logoUrl"),
                            jsonObject.getString("channelName")
                    ));
                }
            } catch (JSONException e) {
                Log.d(TAG, "Error loading recent channels: " + e);
            }
        }

        // Check if the channel with the given playId already exists and remove it if found
        for (Iterator<Channel> iterator = recentChannels.iterator(); iterator.hasNext(); ) {
            Channel channel = iterator.next();
            if (channel.playId.equals(playId)) {
                iterator.remove();
            }
        }

        recentChannels.add(0, new Channel(playId, logoUrl, channelName));

        // Keep only the latest 5 channels
        if (recentChannels.size() > 5) {
            recentChannels.remove(recentChannels.size() - 1);
        }

        // Convert updated list to JSON array and save back to preferences
        JSONArray jsonArray = new JSONArray();
        for (Channel channel : recentChannels) {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("playId", channel.playId);
                jsonObject.put("logoUrl", channel.logoUrl);
                jsonObject.put("channelName", channel.channelName);
                jsonArray.put(jsonObject);
            } catch (JSONException e) {
                Log.d(TAG, String.valueOf(e));
            }
        }

        preferenceManager.setKey(RECENT_CHANNELS_KEY, jsonArray.toString());
    }


    private void loadRecentChannels() {
        SkySharedPref preferenceManager = new SkySharedPref(this);
        String channelData = preferenceManager.getKey(RECENT_CHANNELS_KEY);

        Log.d(TAG, "Channel Data from Shared Preferences: " + channelData);

        if (channelData != null && !channelData.isEmpty()) {

            recentChannels.clear(); // Clear existing list
            Log.d("RIX", "I WAS HERE");
            try {
                JSONArray jsonArray = new JSONArray(channelData);

//                // Start iterating
//                for (int i = 0; i < jsonArray.length(); i++) {

                // Iterate in reverse
                for (int i = jsonArray.length() - 1; i >= 0; i--) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String playId = jsonObject.getString("playId");
                    String logoUrl = jsonObject.getString("logoUrl");
                    String channelName = jsonObject.getString("channelName");

                    // Log each channel's details to confirm parsing
                    Log.d(TAG, "Parsed Channel - Play ID: " + playId + ", Logo URL: " + logoUrl + ", Name: " + channelName);

                    recentChannels.add(new Channel(playId, logoUrl, channelName));
                }
            } catch (JSONException e) {
                Log.e(TAG, "JSON parsing error in loadRecentChannels: " + e.getMessage());
            }
        }

        for (Channel channel : recentChannels) {
            String formattedPlayId = channel.playId.contains("?")
                    ? channel.playId.indexOf('?') == 0
                    ? "??" + channel.playId.substring(1)
                    : channel.playId.replaceFirst("\\?", "??") 
                    : channel.playId + "??";

            Log.d(TAG, "Injecting Channel into WebView - Name: " + channel.channelName + ", Play ID: " + formattedPlayId);

            injectTVChannel(channel.channelName, formattedPlayId, channel.logoUrl);
        }

    }


}
