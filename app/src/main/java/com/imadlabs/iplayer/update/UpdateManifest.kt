package com.imadlabs.iplayer.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Version manifest published with each release (see docs/UPDATES.md). Example:
 * {
 *   "schema": 1, "channel": "stable",
 *   "latest": {
 *     "versionName": "1.1.0", "versionCode": 2, "releaseDate": "2026-11-01",
 *     "changelog": ["Faster library scan"], "minSupportedVersionCode": 1, "mandatory": false,
 *     "releasePage": "https://github.com/ImadKhan1999/iPlayer/releases/tag/v1.1.0",
 *     "apks": [{ "abi": "arm64-v8a", "url": "https://github.com/...apk", "sha256": "…", "sizeBytes": 123 }]
 *   }
 * }
 */
@Serializable
data class UpdateManifest(
    val schema: Int,
    val channel: String = "stable",
    val latest: Release,
) {
    @Serializable
    data class Release(
        val versionName: String,
        val versionCode: Long,
        val releaseDate: String? = null,
        val changelog: List<String> = emptyList(),
        val minSupportedVersionCode: Long = 0,
        val mandatory: Boolean = false,
        val releasePage: String? = null,
        val apks: List<Apk> = emptyList(),
    )

    @Serializable
    data class Apk(
        val abi: String,
        val url: String,
        val sha256: String,
        @SerialName("sizeBytes") val sizeBytes: Long,
    )
}

sealed interface ManifestResult {
    data class Valid(val manifest: UpdateManifest) : ManifestResult
    data class Invalid(val reason: String) : ManifestResult
}

object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = false }
    private val sha256 = Regex("^[0-9a-fA-F]{64}$")
    private val versionName = Regex("""^\d+(\.\d+){0,3}([-+][0-9A-Za-z.\-]{1,20})?$""")
    const val MAX_APK_BYTES = 400L * 1024 * 1024

    /** Parses and validates every field; anything unexpected rejects the whole manifest. */
    fun parse(text: String, trustedHosts: Set<String>): ManifestResult {
        val m = try { json.decodeFromString(UpdateManifest.serializer(), text) }
        catch (e: Exception) { return ManifestResult.Invalid("Malformed manifest: ${e.javaClass.simpleName}") }
        if (m.schema != 1) return ManifestResult.Invalid("Unsupported schema ${m.schema}")
        if (m.channel !in setOf("stable", "beta")) return ManifestResult.Invalid("Unknown channel")
        val r = m.latest
        if (r.versionCode <= 0) return ManifestResult.Invalid("versionCode must be positive")
        if (!versionName.matches(r.versionName)) return ManifestResult.Invalid("Invalid versionName")
        if (r.minSupportedVersionCode < 0 || r.minSupportedVersionCode > r.versionCode) return ManifestResult.Invalid("Invalid minSupportedVersionCode")
        if (r.changelog.size > 50 || r.changelog.any { it.length > 500 }) return ManifestResult.Invalid("Changelog too long")
        r.releaseDate?.let { if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(it)) return ManifestResult.Invalid("Invalid releaseDate") }
        r.releasePage?.let { if (!isTrustedHttps(it, trustedHosts)) return ManifestResult.Invalid("Untrusted release page") }
        for (a in r.apks) {
            if (!isTrustedHttps(a.url, trustedHosts)) return ManifestResult.Invalid("Untrusted APK location")
            if (!sha256.matches(a.sha256)) return ManifestResult.Invalid("Invalid checksum")
            if (a.sizeBytes <= 0 || a.sizeBytes > MAX_APK_BYTES) return ManifestResult.Invalid("Invalid APK size")
            if (a.abi.isBlank() || a.abi.length > 20) return ManifestResult.Invalid("Invalid ABI")
        }
        return ManifestResult.Valid(m)
    }

    fun isTrustedHttps(url: String, trustedHosts: Set<String>): Boolean {
        val u = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        if (u.scheme?.lowercase() != "https" || u.userInfo != null) return false
        val host = u.host?.lowercase() ?: return false
        return trustedHosts.any { host == it || host.endsWith(".$it") }
    }
}

sealed interface UpdateDecision {
    data object UpToDate : UpdateDecision
    data class Available(val release: UpdateManifest.Release, val mandatory: Boolean, val skipped: Boolean) : UpdateDecision
}

object UpdatePolicy {
    /** Decisions use integer version codes only; a lower or equal code is never offered (no downgrades). */
    fun decide(installedCode: Long, manifest: UpdateManifest, skippedCode: Long): UpdateDecision {
        val r = manifest.latest
        if (r.versionCode <= installedCode) return UpdateDecision.UpToDate
        val mandatory = r.mandatory || installedCode < r.minSupportedVersionCode
        return UpdateDecision.Available(r, mandatory, skipped = !mandatory && skippedCode == r.versionCode)
    }

    /** Picks the APK for the device's preferred ABI, falling back to the universal build. */
    fun pickApk(apks: List<UpdateManifest.Apk>, deviceAbis: List<String>): UpdateManifest.Apk? =
        deviceAbis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.abi == abi } }
            ?: apks.firstOrNull { it.abi == "universal" }
}
