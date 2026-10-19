/**
 * MovieBox (MVBox) Dynamic Engine Script for SkyTV / JGoRunner
 *
 * Host this file on GitHub (e.g., in your repository https://github.com/atanuroy22/jiotv_go_app):
 * File URL: https://raw.githubusercontent.com/atanuroy22/jiotv_go_app/main/moviebox.js
 *
 * Add this key to your server.json:
 * {
 *   "moviebox_code_url": "https://raw.githubusercontent.com/atanuroy22/jiotv_go_app/main/moviebox.js"
 * }
 */

/**
 * Returns complete MovieBox configuration.
 * When MovieBox updates their release or secret on their server,
 * simply edit versionName, versionCode, or gatewaySecret here on GitHub!
 */
function getMovieBoxConfig() {
    return {
        baseUrl: "https://api.aoneroom.com",
        packageName: "com.community.oneroom",
        versionName: "4.0.03.0922.03",
        versionCode: 50020131,
        gatewaySecret: "76iRl07s0xSN9jqmEWAt79EBJZulIQIsV64FZr2O",
        userAgent: "com.community.oneroom/4.0.03.0922.03"
    };
}

/**
 * Custom request signing function.
 * Returning null uses standard built-in HmacMD5 signing with active gatewaySecret.
 */
function signRequest(method, path, contentType, contentLength, timestamp, bodyMd5) {
    return null;
}

/**
 * Standard filter items passthrough.
 */
function transformFilterItems(rawJsonString, tabId) {
    return rawJsonString;
}

/**
 * Standard play-info stream payload passthrough.
 */
function transformPlayInfo(rawJsonString, subjectId) {
    return rawJsonString;
}

/**
 * Standard browse list passthrough.
 */
function transformSubjectList(rawJsonString, page, tabId) {
    return rawJsonString;
}

/**
 * Standard search list passthrough.
 */
function transformSearchList(rawJsonString, keyword, page) {
    return rawJsonString;
}

/**
 * Standard episode resource passthrough.
 */
function transformEpisodes(rawJsonString, subjectId) {
    return rawJsonString;
}

/**
 * Standard subtitle captions passthrough.
 */
function transformCaptions(rawJsonString, subjectId, streamId) {
    return rawJsonString;
}

/**
 * Standard search suggestions passthrough.
 */
function transformSuggest(rawJsonString, keyword) {
    return rawJsonString;
}

/**
 * Custom direct stream resolution override.
 * Returning null lets the app proceed with standard play-info resolution.
 */
function resolveCustomStream(subjectId, se, ep) {
    return null;
}
