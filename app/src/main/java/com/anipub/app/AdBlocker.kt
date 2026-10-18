package com.anipub.app

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Blocks requests to ad/tracker hosts (including requests made from inside iframes),
 * stops ad redirects from hijacking the app's main page, and reports HLS (.m3u8) requests.
 */
class AdBlocker(
    private val allowedPageHosts: Set<String>,
    private val onStream: ((url: String, headers: Map<String, String>) -> Unit)? = null
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null

        if (isBlocked(uri)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        // Runs on a background thread, so the callback must be thread-safe.
        if (onStream != null && uri.path?.endsWith(".m3u8", ignoreCase = true) == true) {
            onStream.invoke(uri.toString(), request.requestHeaders ?: emptyMap())
        }
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val r = request ?: return false
        if (!r.isForMainFrame) return false          // iframe loads are handled by the interceptor
        val url = r.url
        if (url.scheme == "file") return false
        val host = url.host ?: return true
        return !matches(host, allowedPageHosts)      // block every other top-level navigation
    }

    companion object {
        private val HOSTS: Set<String> = setOf(
        "2mdn.net", "3lift.com", "a-ads.com", "acscdn.com", "ad-maven.com", "adcash.com",
        "adcolony.com", "adform.com", "adform.net", "adition.com", "adjust.com", "adk2.com",
        "admaven.com", "adnexus.net", "adnium.com", "adnxs.com", "adroll.com", "ads.yahoo.com",
        "adsafeprotected.com", "adsco.re", "adservice.google.com", "adskeeper.com",
        "adspyglass.com", "adsrvr.org", "adsterra.com", "adsystem.com", "adtechus.com",
        "advertising.com", "adx.com", "amazon-adsystem.com", "applovin.com", "bebi.com",
        "betteradsystem.com", "bidswitch.net", "bidvertiser.com", "bluekai.com", "branch.io",
        "casalemedia.com", "chartboost.com", "clickadu.com", "clksite.com", "criteo.com",
        "demdex.net", "dolohen.com", "doubleclick.net", "effectivegatecpm.com",
        "ero-advertising.com", "exdynsrv.com", "exoclick.com", "exosrv.com", "galaksion.com",
        "google-analytics.com", "googleadservices.com", "googlesyndication.com",
        "googletagmanager.com", "googletagservices.com", "highperformanceformat.com",
        "highrevenuegate.com", "hilltopads.net", "histats.com", "hotjar.com", "juicyads.com",
        "kinoplay.pw", "luugy.com", "mc.yandex.ru", "media.net", "mgcash.com", "mgid.com",
        "mixpanel.com", "moatads.com", "monetag.com", "onclickads.net", "onclickalgo.com",
        "onclickmax.com", "onclkds.com", "openx.net", "outbrain.com", "popads.net",
        "popcash.net", "profitablecpmrate.com", "propellerads.com", "pubfuture.com",
        "pubmatic.com", "pushame.com", "pushwhy.com", "quantserve.com", "revcontent.com",
        "richads.com", "rtbsuccess.com", "rubiconproject.com", "scorecardresearch.com",
        "segment.io", "serving-sys.com", "sharethrough.com", "shorte.st", "smartadserver.com",
        "spotxchange.com", "taboola.com", "teads.tv", "trafficjunky.com", "trafficjunky.net",
        "trafficstars.com", "tsyndicate.com", "unityads.unity3d.com", "yieldmo.com",
        "yllix.com", "zergnet.com"
        )

        private val PARTS = listOf(
        "/popunder", "/pop.js", "/pop-under", "/adserver", "/ads.js", "/ad.js", "/banner_ads",
        "/vast.xml", "/vast?", "popads", "/prebid", "zoneid=", "iclick-v", "/iclick",
        "ampproject.org/ads", "bing.com/ads", "/ads/", "/pagead/", "/adsense/", "/banner-ads/",
        "/adframe/", "/redirect.php"
        )

        private fun matches(host: String, set: Set<String>): Boolean {
            var h = host.lowercase()
            while (true) {
                if (h in set) return true
                val i = h.indexOf('.')
                if (i < 0) return false
                h = h.substring(i + 1)
            }
        }

        fun isBlocked(uri: Uri): Boolean {
            val host = uri.host
            if (host != null && matches(host, HOSTS)) return true
            val s = uri.toString().lowercase()
            return PARTS.any { s.contains(it) }
        }
    }
}
