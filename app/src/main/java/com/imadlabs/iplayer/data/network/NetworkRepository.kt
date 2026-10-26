package com.imadlabs.iplayer.data.network

import android.net.Uri
import com.imadlabs.iplayer.data.db.AppDatabase
import com.imadlabs.iplayer.data.db.NetworkProtocol
import com.imadlabs.iplayer.data.db.NetworkSource
import com.imadlabs.iplayer.playback.CredentialsProvider
import com.imadlabs.iplayer.util.CryptoBox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Saved network sources. Passwords are encrypted with the Android Keystore and never logged. */
class NetworkRepository(private val db: AppDatabase) : CredentialsProvider {
    val sources: Flow<List<NetworkSource>> = db.networkSources().all()

    suspend fun save(
        id: Long, name: String, protocol: NetworkProtocol, host: String, port: Int?, path: String,
        username: String?, password: String?, domain: String?, keepExistingPassword: Boolean,
    ): Long {
        val existing = if (id != 0L) db.networkSources().get(id) else null
        val encrypted = when {
            !password.isNullOrEmpty() -> CryptoBox.encrypt(password)
            keepExistingPassword -> existing?.encryptedPassword
            else -> null
        }
        val src = NetworkSource(id, name.ifBlank { host }, protocol, host.trim(), port, normalizePath(path),
            username?.takeIf { it.isNotBlank() }, encrypted, domain?.takeIf { it.isNotBlank() },
            existing?.createdAt ?: System.currentTimeMillis())
        val newId = db.networkSources().upsert(src)
        return if (id != 0L) id else newId
    }

    suspend fun delete(id: Long) = db.networkSources().delete(id)

    suspend fun get(id: Long) = db.networkSources().get(id)

    fun password(source: NetworkSource): String? = source.encryptedPassword?.let(CryptoBox::decrypt)

    companion object {
        fun normalizePath(path: String): String = path.trim().trim('/').let { if (it.isEmpty()) "" else "/$it" }

        /** Location without credentials; credentials are passed to LibVLC as media options. */
        fun rootUri(s: NetworkSource): Uri {
            val port = s.port?.takeIf { it != s.protocol.defaultPort }?.let { ":$it" }.orEmpty()
            return Uri.parse("${s.protocol.scheme}://${s.host}$port${s.path}/")
        }
    }

    /** LibVLC media options carrying the saved credentials for the matching host. */
    override suspend fun mediaOptionsFor(uri: Uri): List<String> {
        val host = uri.host ?: return emptyList()
        val scheme = uri.scheme?.lowercase() ?: return emptyList()
        val src = sources.first().firstOrNull { it.host.equals(host, true) && it.protocol.scheme == scheme }
            ?: return emptyList()
        val user = src.username ?: return emptyList()
        val pass = password(src).orEmpty()
        return when (src.protocol) {
            NetworkProtocol.Smb -> buildList {
                add(":smb-user=$user"); add(":smb-pwd=$pass")
                src.domain?.let { add(":smb-domain=$it") }
            }
            NetworkProtocol.Ftp, NetworkProtocol.Ftps -> listOf(":ftp-user=$user", ":ftp-pwd=$pass")
            NetworkProtocol.Sftp -> listOf(":sftp-user=$user", ":sftp-pwd=$pass")
            // HTTP authentication is answered through LibVLC's login dialog (see credentialsFor).
            NetworkProtocol.Http, NetworkProtocol.Https, NetworkProtocol.Nfs -> emptyList()
        }
    }

    override suspend fun credentialsFor(uri: Uri): Pair<String, String>? {
        val host = uri.host ?: return null
        val src = sources.first().firstOrNull { it.host.equals(host, true) } ?: return null
        val user = src.username ?: return null
        return user to password(src).orEmpty()
    }
}
