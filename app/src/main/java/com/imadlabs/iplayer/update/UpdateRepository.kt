package com.imadlabs.iplayer.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.imadlabs.iplayer.BuildConfig
import com.imadlabs.iplayer.data.settings.AppStateRepository
import com.imadlabs.iplayer.data.settings.SettingsRepository
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val release: UpdateManifest.Release, val mandatory: Boolean, val skipped: Boolean, val checkedAt: Long) : UpdateState
    data class Failed(val message: String, val offline: Boolean) : UpdateState
}

/**
 * Checks the configured HTTPS manifest for a newer build. It never installs anything by itself:
 * the user decides, and installation always goes through Android's package installer.
 */
class UpdateRepository(
    private val context: Context,
    private val appState: AppStateRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val startupCheckDone = AtomicBoolean(false)

    val installedVersionCode: Long by lazy {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info)
    }

    val installedVersionName: String by lazy {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
    }

    /** Runs at most once per process launch, in the background, and never shows offline errors. */
    fun checkOnStartup() {
        if (!startupCheckDone.compareAndSet(false, true)) return
        scope.launch {
            if (!settings.snapshot().autoUpdateCheck) return@launch
            val cached = appState.lastUpdateCheck.first()
            // Use the cached result when the last successful check is recent, avoiding needless traffic.
            if (cached.manifestJson != null && System.currentTimeMillis() - cached.checkedAt < CACHE_MS) {
                evaluate(cached.manifestJson, cached.checkedAt, fromCache = true)
                return@launch
            }
            check(userInitiated = false)
        }
    }

    suspend fun check(userInitiated: Boolean) {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { fetch(manifestUrl) }
        val now = System.currentTimeMillis()
        result.fold(
            onSuccess = { text ->
                if (evaluate(text, now, fromCache = false)) appState.saveUpdateCheck(now, text)
            },
            onFailure = { e ->
                val offline = e is UnknownHostException || e is SocketTimeoutException || e is java.net.ConnectException
                Diagnostics.log("Update", "Update check failed: ${e.javaClass.simpleName}")
                val cached = appState.lastUpdateCheck.first()
                if (!userInitiated && cached.manifestJson != null) {
                    evaluate(cached.manifestJson, cached.checkedAt, fromCache = true)
                } else {
                    _state.value = UpdateState.Failed(
                        when {
                            offline -> "You appear to be offline. iPlayer works normally; try again when connected."
                            e is HttpStatusException && e.code == 404 -> "No published release was found at the update address yet."
                            else -> "Couldn't check for updates right now."
                        }, offline)
                }
            },
        )
    }

    private suspend fun evaluate(text: String, checkedAt: Long, fromCache: Boolean): Boolean {
        return when (val parsed = ManifestParser.parse(text, TRUSTED_HOSTS)) {
            is ManifestResult.Invalid -> {
                Diagnostics.log("Update", "Rejected manifest: ${parsed.reason}")
                _state.value = UpdateState.Failed("The update information was invalid and was ignored.", false)
                false
            }
            is ManifestResult.Valid -> {
                val skipped = appState.skippedVersionCode.first()
                _state.value = when (val d = UpdatePolicy.decide(installedVersionCode, parsed.manifest, skipped)) {
                    UpdateDecision.UpToDate -> UpdateState.UpToDate(checkedAt)
                    is UpdateDecision.Available -> UpdateState.Available(d.release, d.mandatory, d.skipped, checkedAt)
                }
                if (!fromCache) Diagnostics.log("Update", "Manifest OK: latest=${parsed.manifest.latest.versionCode} installed=$installedVersionCode")
                true
            }
        }
    }

    suspend fun skip(release: UpdateManifest.Release) {
        appState.skipVersion(release.versionCode)
        val s = _state.value
        if (s is UpdateState.Available) _state.value = s.copy(skipped = true)
    }

    /** Dismissed for this launch only ("Later"). */
    fun dismissForSession() {
        val s = _state.value
        if (s is UpdateState.Available && !s.mandatory) _state.value = s.copy(skipped = true)
    }

    fun apkFor(release: UpdateManifest.Release): UpdateManifest.Apk? =
        UpdatePolicy.pickApk(release.apks, Build.SUPPORTED_ABIS.toList())

    private fun fetch(url: String): Result<String> = runCatching {
        if (!ManifestParser.isTrustedHttps(url, TRUSTED_HOSTS)) throw IOException("Manifest URL is not a trusted HTTPS address")
        var current = URL(url)
        repeat(5) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "iPlayer/${BuildConfig.VERSION_NAME} (Android)")
            }
            try {
                when (val code = conn.responseCode) {
                    in 300..399 -> {
                        val next = conn.getHeaderField("Location") ?: throw IOException("Redirect without location")
                        val resolved = URL(current, next)
                        if (!ManifestParser.isTrustedHttps(resolved.toString(), TRUSTED_HOSTS)) throw IOException("Redirect to untrusted host")
                        current = resolved
                    }
                    200 -> {
                        val bytes = conn.inputStream.use { it.readNBytesCompat(MAX_MANIFEST_BYTES + 1) }
                        if (bytes.size > MAX_MANIFEST_BYTES) throw IOException("Manifest too large")
                        return@runCatching String(bytes, Charsets.UTF_8)
                    }
                    else -> throw HttpStatusException(code)
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        const val CACHE_MS = 60 * 60 * 1000L
        const val MAX_MANIFEST_BYTES = 64 * 1024
        /** Release assets are served from GitHub; redirects are only followed within these hosts. */
        val TRUSTED_HOSTS = setOf("github.com", "githubusercontent.com", "imadkhan1999.github.io")

        fun signingDigests(context: Context, packageName: String? = null, archivePath: String? = null): Set<String> {
            val pm = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
            val info = when {
                archivePath != null -> pm.getPackageArchiveInfo(archivePath, flags)
                else -> pm.getPackageInfo(packageName ?: context.packageName, flags)
            } ?: return emptySet()
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val si = info.signingInfo ?: return emptySet()
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else @Suppress("DEPRECATION") info.signatures
            val md = java.security.MessageDigest.getInstance("SHA-256")
            return sigs.orEmpty().map { s -> md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
        }
    }
}

internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
