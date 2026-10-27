package com.imadlabs.iplayer.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.widget.Toast
import com.imadlabs.iplayer.BuildConfig
import com.imadlabs.iplayer.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface DownloadStatus {
    data class Progress(val downloaded: Long, val total: Long) : DownloadStatus
    data object Verifying : DownloadStatus
    data class Ready(val file: File) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

/**
 * Direct-distribution updater: downloads the release APK over HTTPS from a trusted host, resumes
 * interrupted downloads, verifies the SHA-256 from the manifest and the signing certificate
 * against the installed app, then hands the file to Android's PackageInstaller. The user always
 * confirms the installation in the system dialog. Downloaded content is never executed by iPlayer.
 */
class ApkUpdater(private val context: Context) {
    private val dir = File(context.cacheDir, "updates")

    fun download(apk: UpdateManifest.Apk, versionCode: Long): Flow<DownloadStatus> = flow {
        if (!ManifestParser.isTrustedHttps(apk.url, UpdateRepository.TRUSTED_HOSTS)) {
            emit(DownloadStatus.Failed("The download address isn't trusted.")); return@flow
        }
        dir.mkdirs()
        dir.listFiles()?.filter { !it.name.startsWith("iplayer-$versionCode") }?.forEach { it.delete() }
        val target = File(dir, "iplayer-$versionCode-${apk.abi}.apk")
        val part = File(dir, target.name + ".part")
        if (target.exists() && sha256(target).equals(apk.sha256, true)) {
            emit(DownloadStatus.Verifying)
            emit(verifySignature(target)); return@flow
        }
        val free = StatFs(dir.path).availableBytes
        if (free < apk.sizeBytes - part.length() + 20L * 1024 * 1024) {
            emit(DownloadStatus.Failed("Not enough free storage. ${(apk.sizeBytes / 1_048_576)} MB is needed.")); return@flow
        }
        try {
            var url = URL(apk.url)
            var redirects = 0
            while (true) {
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "iPlayer/${BuildConfig.VERSION_NAME} (Android)")
                    if (part.length() > 0) setRequestProperty("Range", "bytes=${part.length()}-")
                }
                try {
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val next = URL(url, conn.getHeaderField("Location") ?: throw IOException("Bad redirect"))
                        if (!ManifestParser.isTrustedHttps(next.toString(), UpdateRepository.TRUSTED_HOSTS) || ++redirects > 5)
                            throw IOException("Redirected to an untrusted location")
                        url = next
                        continue
                    }
                    val append = code == 206
                    if (code != 200 && !append) throw IOException("Server returned HTTP $code")
                    if (!append) part.delete()
                    RandomAccessFile(part, "rw").use { raf ->
                        raf.seek(if (append) part.length() else 0)
                        var done = raf.filePointer
                        val buf = ByteArray(64 * 1024)
                        var lastEmit = 0L
                        conn.inputStream.use { input ->
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                raf.write(buf, 0, n)
                                done += n
                                if (done > ManifestParser.MAX_APK_BYTES) throw IOException("Download larger than expected")
                                if (done - lastEmit > 256 * 1024) { lastEmit = done; emit(DownloadStatus.Progress(done, apk.sizeBytes)) }
                            }
                        }
                    }
                    break
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: IOException) {
            Diagnostics.log("Update", "Download interrupted", e)
            emit(DownloadStatus.Failed("The download was interrupted. Try again to resume it.")); return@flow
        }
        emit(DownloadStatus.Verifying)
        if (part.length() != apk.sizeBytes || !sha256(part).equals(apk.sha256, true)) {
            part.delete()
            emit(DownloadStatus.Failed("The downloaded file didn't match the published checksum and was deleted.")); return@flow
        }
        part.renameTo(target)
        emit(verifySignature(target))
    }.flowOn(Dispatchers.IO)

    private fun verifySignature(file: File): DownloadStatus {
        val installed = UpdateRepository.signingDigests(context)
        val downloaded = runCatching { UpdateRepository.signingDigests(context, archivePath = file.path) }.getOrDefault(emptySet())
        if (downloaded.isEmpty()) {
            file.delete()
            return DownloadStatus.Failed("The update's signature couldn't be read, so it wasn't installed. Use the release page instead.")
        }
        if (installed.intersect(downloaded).isEmpty()) {
            file.delete()
            return DownloadStatus.Failed("The update is signed by a different key than this installation and was rejected.")
        }
        return DownloadStatus.Ready(file)
    }

    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Starts a PackageInstaller session; Android shows its own confirmation dialog. */
    fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("iplayer.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(context, id, intent, flags)
            session.commit(pi.intentSender)
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Receives PackageInstaller results and forwards the system confirmation screen to the user. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> Toast.makeText(context, "Update cancelled", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Diagnostics.log("Update", "Install failed: $msg")
                Toast.makeText(context, "The update couldn't be installed.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
