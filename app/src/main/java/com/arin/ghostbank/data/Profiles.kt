package com.arin.ghostbank.data

import com.arin.ghostbank.model.GhostProfile
import com.arin.ghostbank.model.LeakReport
import com.arin.ghostbank.model.Settings
import com.arin.ghostbank.model.StealthLevel
import kotlin.random.Random

object Profiles {

    const val FALLBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val majorVersions = listOf("128", "129", "130", "131", "132", "133")
    private val locales = listOf("en-IN,en;q=0.9", "en-GB,en;q=0.8", "en-US,en;q=0.9", "en-IN,en-GB;q=0.8,en;q=0.7")
    private val encodings = listOf("gzip, deflate, br, zstd", "gzip, deflate, br", "gzip, deflate")
    private val timeZones = listOf("Asia/Kolkata", "Asia/Kolkata", "Asia/Kolkata", "Asia/Dubai", "Asia/Singapore")

    /** Header keys Chrome is free to order differently without looking wrong. */
    private val permutable = listOf(
        "Accept",
        "Sec-Fetch-Site",
        "Sec-Fetch-Mode",
        "Sec-Fetch-User",
        "Sec-Fetch-Dest",
        "Accept-Encoding",
        "Accept-Language"
    )

    /**
     * Deterministic per seed: the same account always gets the same frozen profile,
     * two accounts never share one. Burned profiles are re-seeded with the burn counter.
     */
    fun freeze(seed: String, deviceUserAgent: String, settings: Settings, burn: Int = 0): GhostProfile {
        val rnd = Random((seed + "#" + burn).hashCode().toLong())
        val major = majorVersions[rnd.nextInt(majorVersions.size)]
        val minor = listOf("0.0.0", "0.0.0", "0.6362.87", "0.6478.126")[rnd.nextInt(4)]
        val brandFull = listOf("Google Chrome", "Chromium", "Google Chrome")[rnd.nextInt(3)]
        val ua = if (deviceUserAgent.contains("Chrome")) {
            deviceUserAgent.replace(Regex("Chrome/[0-9.]+"), "Chrome/$major.$minor")
        } else {
            FALLBACK_USER_AGENT
        }
        val hints = "\"$brandFull\";v=\"$major\", \"Chromium\";v=\"$major\", \"Not?A_Brand\";v=\"24\""
        val order = permutable.shuffled(rnd)
        return GhostProfile(
            id = seed.take(8) + "-" + burn,
            userAgent = ua,
            clientHints = hints,
            headerOrder = order,
            cipherOrderSeed = rnd.nextInt(1000),
            h2WindowUpdate = listOf(15663105, 6291456, 1048576)[rnd.nextInt(3)],
            locale = locales[rnd.nextInt(locales.size)],
            timeZone = timeZones[rnd.nextInt(timeZones.size)],
            exitCountry = "",
            acceptEncoding = encodings[rnd.nextInt(encodings.size)],
            burned = burn > 0
        )
    }

    /** Knuth's Poisson sampler scaled to the mean gap. */
    fun poissonDelay(meanMs: Long, enabled: Boolean, rnd: Random): Long {
        if (!enabled) return (meanMs * (0.7 + rnd.nextDouble() * 0.6)).toLong()
        var k = 0
        var p = 1.0
        val limit = Math.exp(-1.0)
        do {
            k++
            p *= rnd.nextDouble()
        } while (p > limit && k < 12)
        val raw = (k * meanMs).toLong()
        return raw.coerceIn(meanMs / 3, meanMs * 5)
    }

    fun score(
        settings: Settings,
        leak: LeakReport?,
        proxyCount: Int,
        torMode: Boolean
    ): Pair<Int, List<String>> {
        var score = 0
        val reasons = mutableListOf<String>()
        when (settings.stealthLevel) {
            StealthLevel.STANDARD -> { score += 12; reasons += "Standard level: Chrome headers, no exit required" }
            StealthLevel.DEEP -> { score += 22; reasons += "Deep level: cover traffic and ghost idle on" }
            StealthLevel.GHOST -> { score += 32; reasons += "Ghost level: proxy mandatory" }
        }
        if (torMode) { score += 12; reasons += "Tor exit configured" }
        else if (proxyCount > 0) { score += 10; reasons += "$proxyCount proxy exit(s) with per-request rotation" }
        else { reasons += "No exit pool — the raw mobile IP will be visible" }
        if (settings.doh) { score += 8; reasons += "DNS over HTTPS keeps lookups inside the tunnel" }
        else reasons += "DNS resolves directly (the ISP sees every name)"
        if (settings.killSwitch) { score += 7; reasons += "Kill-switch: no exit means no traffic" }
        else reasons += "Kill-switch off — a dropped exit still leaks"
        if (settings.geoMatch) { score += 6; reasons += "Geo-matched exit enforced" } else reasons += "Exit geography not matched"
        if (settings.decoyBrowsing) { score += 5; reasons += "Decoy browsing before each leg" } else reasons += "Decoy browsing off"
        if (settings.ghostIdle) { score += 4; reasons += "Ghost idle keep-alives then clean close" } else reasons += "Ghost idle off"
        if (settings.poisson) { score += 5; reasons += "Poisson jitter instead of fixed gaps" } else reasons += "Fixed randomness, not Poisson"
        if (settings.diurnal) { score += 3; reasons += "Diurnal window 09:00-23:00" }
        if (settings.entropyBudget in 1..300) { score += 4; reasons += "Entropy budget caps the request count" }
        if (leak == null) { reasons += "No Leak Lab run yet — the exit is unverified" }
        else if (leak.ok) {
            score += 10
            reasons += "Leak Lab verified the exit (${leak.ip})"
            if (leak.timeZoneSkewMin > 120) { score -= 6; reasons += "Time-zone skew ${leak.timeZoneSkewMin} min against the exit" }
        } else reasons += "Leak Lab exists but the last check failed"
        reasons += "The destination bank still knows the account number by design"
        val clamped = score.coerceIn(0, 100)
        return clamped to reasons
    }
}
