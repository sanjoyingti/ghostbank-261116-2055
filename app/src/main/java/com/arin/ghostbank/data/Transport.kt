package com.arin.ghostbank.data

import android.content.Context
import android.webkit.WebSettings
import com.arin.ghostbank.model.GhostProfile
import com.arin.ghostbank.model.LeakReport
import com.arin.ghostbank.model.ProxyScheme
import com.arin.ghostbank.model.Settings
import com.arin.ghostbank.model.StealthLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One HTTP engine only (OkHttp). Chrome header order, Chrome cipher order, ALPN h2,
 * connection reuse then deliberate close, ephemeral cookies, rotating exit, DoH, kill-switch.
 */
class Transport(context: Context) {

    data class Attempt(
        val code: Int?,
        val durationMs: Long,
        val snippet: String,
        val error: String?,
        val exit: String?
    ) {
        val answered: Boolean get() = code != null
        val blocked: Boolean get() = code == 403 || code == 429 || code == 503
        val healthy: Boolean get() = code != null && code in 200..399
    }

    private val appContext = context.applicationContext
    private val pool = ConnectionPool(6, 60_000, TimeUnit.MILLISECONDS)
    private val dispatcher = Dispatcher().apply {
        maxRequests = 4
        maxRequestsPerHost = 4
    }
    private val cursor = AtomicInteger(0)
    private val jar = EphemeralCookieJar()
    private val json = Json { ignoreUnknownKeys = true }

    /** The device's own real Chrome user agent, so the string is never invented. */
    val userAgent: String = runCatching { WebSettings.getDefaultUserAgent(appContext) }
        .getOrNull()
        ?.takeIf { it.contains("Chrome") }
        ?: Profiles.FALLBACK_USER_AGENT

    fun clearCookies() = jar.clear()

    fun cookieCount(): Int = jar.size()

    private val preferenceOrder = listOf(
        "TLS_AES_128_GCM_SHA256",
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "ECDHE-ECDSA-AES128-GCM-SHA256",
        "ECDHE-RSA-AES128-GCM-SHA256",
        "ECDHE-ECDSA-AES256-GCM-SHA384",
        "ECDHE-RSA-AES256-GCM-SHA384",
        "ECDHE-ECDSA-CHACHA20-POLY1305",
        "ECDHE-RSA-CHACHA20-POLY1305",
        "ECDHE-RSA-AES128-SHA",
        "ECDHE-RSA-AES256-SHA",
        "AES128-GCM-SHA256",
        "AES256-GCM-SHA384",
        "AES128-SHA",
        "AES256-SHA"
    )

    private fun chromeSpec(seed: Int): ConnectionSpec {
        val base = ConnectionSpec.MODERN_TLS.cipherSuites ?: emptyList()
        val reordered = base.sortedBy { suite ->
            val idx = preferenceOrder.indexOf(suite.javaName)
            if (idx < 0) 100 else idx
        }
        // Deterministic per-profile rotation inside Chrome's valid set.
        val rotated = if (reordered.isEmpty()) reordered
        else reordered.drop(seed % reordered.size.coerceAtLeast(1)).let { it + reordered.take(seed % reordered.size.coerceAtLeast(1)) }
        return ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .cipherSuites(*rotated.toTypedArray())
            .build()
    }

    private fun requiresExit(settings: Settings): Boolean =
        settings.killSwitch && (settings.stealthLevel == StealthLevel.GHOST || settings.torMode || settings.proxies.isNotEmpty())

    private fun pickProxy(settings: Settings): Proxy? {
        if (settings.torMode) return Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", 9050))
        val list = settings.proxies
        if (list.isEmpty()) return null
        val chosen = list[Math.floorMod(cursor.getAndIncrement(), list.size)]
        val address = InetSocketAddress.createUnresolved(chosen.host, chosen.port)
        return when (chosen.scheme) {
            ProxyScheme.SOCKS5 -> Proxy(Proxy.Type.SOCKS, address)
            ProxyScheme.HTTPS -> Proxy(Proxy.Type.HTTP, address)
        }
    }

    fun exitLabel(settings: Settings): String = when {
        settings.torMode -> "tor://127.0.0.1:9050"
        settings.proxies.isEmpty() -> "direct"
        else -> "pool(${settings.proxies.size})"
    }

    private fun buildClient(settings: Settings, proxy: Proxy?, seed: Int): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectionPool(pool)
            .dispatcher(dispatcher)
            .cookieJar(jar)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(9, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .connectionSpecs(listOf(chromeSpec(seed), ConnectionSpec.CLEARTEXT))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        if (settings.doh) builder.dns(DohDns())
        if (proxy != null) builder.proxy(proxy)
        return builder.build()
    }

    private fun headers(profile: GhostProfile): List<Pair<String, String>> {
        val values = linkedMapOf(
            "sec-ch-ua" to profile.clientHints,
            "sec-ch-ua-mobile" to "?1",
            "sec-ch-ua-platform" to "\"Android\"",
            "Upgrade-Insecure-Requests" to "1",
            "User-Agent" to profile.userAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-User" to "?1",
            "Sec-Fetch-Dest" to "document",
            "Accept-Encoding" to profile.acceptEncoding,
            "Accept-Language" to profile.locale
        )
        val order = listOf(
            "sec-ch-ua",
            "sec-ch-ua-mobile",
            "sec-ch-ua-platform",
            "Upgrade-Insecure-Requests",
            "User-Agent"
        ) + profile.headerOrder.filter { values.containsKey(it) } +
            values.keys.filterNot { key ->
                key in listOf("sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "Upgrade-Insecure-Requests", "User-Agent") ||
                    key in profile.headerOrder
            }
        return order.mapNotNull { key -> values[key]?.let { key to it } }
    }

    suspend fun fetch(
        url: String,
        settings: Settings,
        profile: GhostProfile,
        referer: String? = null
    ): Attempt = withContext(Dispatchers.IO) {
        val exit = pickProxy(settings)
        if (requiresExit(settings) && exit == null) {
            return@withContext Attempt(null, 0L, "", "NO_EXIT_CONFIGURED", "none")
        }
        val client = buildClient(settings, exit, profile.cipherOrderSeed)
        val builder = Request.Builder().url(url)
        headers(profile).forEach { (name, value) -> builder.header(name, value) }
        if (referer != null) builder.header("Referer", referer)
        val started = System.currentTimeMillis()
        try {
            client.newCall(builder.build()).execute().use { response ->
                val snippet = runCatching { response.peekBody(4096).string() }.getOrDefault("")
                Attempt(
                    code = response.code,
                    durationMs = System.currentTimeMillis() - started,
                    snippet = snippet.take(700),
                    error = null,
                    exit = exitLabel(settings)
                )
            }
        } catch (t: Throwable) {
            Attempt(null, System.currentTimeMillis() - started, "", t.message ?: t.javaClass.simpleName, exitLabel(settings))
        }
    }

    /** Deliberate close after a run: nothing survives to the next one. */
    fun closeConnections() {
        pool.evictAll()
        jar.clear()
    }

    suspend fun leakCheck(settings: Settings, profile: GhostProfile): LeakReport = withContext(Dispatchers.IO) {
        val exit = pickProxy(settings)
        if (requiresExit(settings) && exit == null) {
            return@withContext LeakReport(ok = false, error = "No exit configured and the kill-switch is on")
        }
        var ip = "—"
        var country = "—"
        val trace = fetch("https://cloudflare.com/cdn-cgi/trace", settings, profile)
        if (trace.healthy) {
            trace.snippet.lineSequence().forEach { line ->
                when {
                    line.startsWith("ip=") -> ip = line.removePrefix("ip=").trim()
                    line.startsWith("loc=") -> country = line.removePrefix("loc=").trim()
                }
            }
        }
        var tls = "—"
        val tlsAttempt = fetch("https://tls.peet.ws/api/all", settings, profile)
        if (tlsAttempt.healthy) {
            tls = runCatching {
                val root = json.parseToJsonElement(tlsAttempt.snippet).jsonObject
                val ja3 = root["tls"]?.jsonObject?.get("ja3_hash")?.jsonPrimitive?.content
                val akamai = root["tls"]?.jsonObject?.get("ja4")?.jsonPrimitive?.content
                listOfNotNull(ja3, akamai).joinToString(" / ").ifEmpty { "unparsed" }
            }.getOrDefault("unparsed")
        }
        val dnsPath = if (settings.doh) "DoH via cloudflare-dns.com" else "system resolver (ISP visible)"
        val skew = timeZoneSkew(country)
        val ok = trace.healthy && tlsAttempt.healthy
        LeakReport(
            ip = ip,
            country = country,
            tlsFingerprint = tls,
            dnsPath = dnsPath,
            timeZoneSkewMin = skew,
            exitLabel = exitLabel(settings),
            ok = ok,
            error = when {
                ok -> null
                !trace.answered && trace.error != null -> trace.error
                !trace.healthy -> "IP echo returned ${trace.code}"
                else -> "TLS fingerprint endpoint returned ${tlsAttempt.code}"
            }
        )
    }

    private fun timeZoneSkew(country: String): Int {
        val expected = EXPECTED_OFFSET_MIN[country.uppercase()] ?: return 0
        val device = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        return kotlin.math.abs(device - expected)
    }

    private inner class DohDns : Dns {
        private val client = OkHttpClient.Builder()
            .callTimeout(6, TimeUnit.SECONDS)
            .build()

        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.isIpLiteral()) return listOf(InetAddress.getByName(hostname))
            val parsed = runCatching {
                val url = HttpUrl.Builder()
                    .scheme("https")
                    .host("cloudflare-dns.com")
                    .addPathSegments("dns-query")
                    .addQueryParameter("name", hostname)
                    .addQueryParameter("type", "A")
                    .build()
                client.newCall(
                    Request.Builder().url(url).header("accept", "application/dns-json").build()
                ).execute().use { response -> response.peekBody(16384).string() }
            }.getOrNull()
            val addresses = parsed?.let { body ->
                runCatching {
                    json.parseToJsonElement(body).jsonObject["Answer"]?.jsonArray
                        ?.mapNotNull { it.jsonObject["data"]?.jsonPrimitive?.content }
                        ?.mapNotNull { data -> runCatching { InetAddress.getByName(data) }.getOrNull() }
                }.getOrNull()
            }
            return if (!addresses.isNullOrEmpty()) addresses else Dns.SYSTEM.lookup(hostname)
        }

        private fun String.isIpLiteral(): Boolean =
            Regex("^[0-9]{1,3}(\\.[0-9]{1,3}){3}$").matches(this)
    }

    private class EphemeralCookieJar : CookieJar {
        private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val bucket = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { incoming ->
                bucket.removeAll { it.name == incoming.name }
                bucket += incoming
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store[url.host]?.filter { it.expiresAt > now } ?: emptyList()
        }

        fun clear() = store.clear()

        fun size(): Int = store.values.sumOf { it.size }
    }

    companion object {
        private val EXPECTED_OFFSET_MIN = mapOf(
            "IN" to 330, "US" to -300, "GB" to 0, "DE" to 60, "NL" to 60,
            "SG" to 480, "FR" to 60, "JP" to 540, "CA" to -300, "AU" to 600, "BR" to -180
        )
    }
}
