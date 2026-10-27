package com.imadlabs.iplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-file playback state: resume position, history, and per-file track/sync choices. */
@Entity(tableName = "playback_records", indices = [Index("lastPlayedAt")])
data class PlaybackRecord(
    @PrimaryKey val uri: String,
    val title: String,
    val isVideo: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,
    val playCount: Int,
    val completed: Boolean,
    val subtitleDelayUs: Long = 0,
    val audioTrackId: Int? = null,
    val subtitleTrackId: Int? = null,
    val externalSubtitleUri: String? = null,
)

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val uri: String,
    val title: String,
    val subtitle: String?,
    val isVideo: Boolean,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_entries",
    foreignKeys = [ForeignKey(
        entity = Playlist::class, parentColumns = ["id"], childColumns = ["playlistId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlistId")],
)
data class PlaylistEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val position: Int,
    val uri: String,
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val isVideo: Boolean,
    val artworkUri: String?,
)

enum class NetworkProtocol(val scheme: String, val label: String, val defaultPort: Int) {
    Smb("smb", "SMB / Windows share", 445),
    Ftp("ftp", "FTP", 21),
    Ftps("ftps", "FTPS", 990),
    Sftp("sftp", "SFTP", 22),
    Nfs("nfs", "NFS", 2049),
    Http("http", "HTTP", 80),
    Https("https", "HTTPS", 443),
}

/** Saved network source. The password is stored only as Android Keystore ciphertext (see CryptoBox). */
@Entity(tableName = "network_sources")
data class NetworkSource(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: NetworkProtocol,
    val host: String,
    val port: Int?,
    val path: String,
    val username: String?,
    val encryptedPassword: String?,
    val domain: String?,
    val createdAt: Long,
)

@Entity(tableName = "excluded_folders")
data class ExcludedFolder(@PrimaryKey val path: String, val addedAt: Long)

/** Persisted play queue so it can be restored after the process dies. */
@Entity(tableName = "queue_entries")
data class QueueEntryRow(
    @PrimaryKey val position: Int,
    val uri: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val isVideo: Boolean,
)

@Entity(tableName = "queue_state")
data class QueueStateRow(
    @PrimaryKey val id: Int = 0,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val shuffleOrder: String,
)

/** User correction of automatic TV-episode classification. */
@Entity(tableName = "classification_overrides")
data class ClassificationOverride(
    @PrimaryKey val uri: String,
    val isEpisode: Boolean,
    val showName: String?,
    val season: Int?,
    val episode: Int?,
)
