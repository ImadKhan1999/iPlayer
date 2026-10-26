package com.imadlabs.iplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackRecordDao {
    @Query("SELECT * FROM playback_records WHERE uri = :uri")
    suspend fun get(uri: String): PlaybackRecord?

    @Upsert
    suspend fun upsert(record: PlaybackRecord)

    @Query("SELECT * FROM playback_records ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun recent(limit: Int = 200): Flow<List<PlaybackRecord>>

    @Query("SELECT * FROM playback_records WHERE isVideo = 1 AND completed = 0 AND positionMs > 5000 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun continueWatching(limit: Int = 20): Flow<List<PlaybackRecord>>

    @Query("SELECT * FROM playback_records WHERE isVideo = 0 AND playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun mostPlayedAudio(limit: Int = 100): Flow<List<PlaybackRecord>>

    @Query("SELECT * FROM playback_records")
    fun all(): Flow<List<PlaybackRecord>>

    @Query("DELETE FROM playback_records WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("DELETE FROM playback_records")
    suspend fun clear()

    @Query("UPDATE playback_records SET positionMs = 0, completed = 1 WHERE uri = :uri")
    suspend fun markWatched(uri: String)

    @Query("UPDATE playback_records SET positionMs = 0, completed = 0 WHERE uri = :uri")
    suspend fun markUnwatched(uri: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun all(): Flow<List<Favorite>>

    @Upsert
    suspend fun add(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun remove(uri: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri)")
    fun isFavorite(uri: String): Flow<Boolean>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun playlists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<Playlist?>

    @Query("SELECT playlistId, COUNT(*) AS count FROM playlist_entries GROUP BY playlistId")
    fun counts(): Flow<List<PlaylistCount>>

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :id ORDER BY position")
    fun entries(id: Long): Flow<List<PlaylistEntry>>

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :id ORDER BY position")
    suspend fun entriesNow(id: Long): List<PlaylistEntry>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntry>)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :id")
    suspend fun clearEntries(id: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Transaction
    suspend fun replaceEntries(id: Long, entries: List<PlaylistEntry>, now: Long) {
        clearEntries(id)
        insertEntries(entries.mapIndexed { i, e -> e.copy(id = 0, playlistId = id, position = i) })
        touch(id, now)
    }

    @Transaction
    suspend fun append(id: Long, entries: List<PlaylistEntry>, now: Long) {
        val start = maxPosition(id) + 1
        insertEntries(entries.mapIndexed { i, e -> e.copy(id = 0, playlistId = id, position = start + i) })
        touch(id, now)
    }
}

data class PlaylistCount(val playlistId: Long, val count: Int)

@Dao
interface NetworkSourceDao {
    @Query("SELECT * FROM network_sources ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<NetworkSource>>

    @Query("SELECT * FROM network_sources WHERE id = :id")
    suspend fun get(id: Long): NetworkSource?

    @Upsert
    suspend fun upsert(source: NetworkSource): Long

    @Query("DELETE FROM network_sources WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExcludedFolderDao {
    @Query("SELECT * FROM excluded_folders ORDER BY path")
    fun all(): Flow<List<ExcludedFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(folder: ExcludedFolder)

    @Query("DELETE FROM excluded_folders WHERE path = :path")
    suspend fun remove(path: String)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_entries ORDER BY position")
    suspend fun entries(): List<QueueEntryRow>

    @Query("SELECT * FROM queue_state WHERE id = 0")
    suspend fun state(): QueueStateRow?

    @Query("DELETE FROM queue_entries")
    suspend fun clearEntries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(rows: List<QueueEntryRow>)

    @Upsert
    suspend fun saveState(state: QueueStateRow)

    @Transaction
    suspend fun replace(rows: List<QueueEntryRow>, state: QueueStateRow) {
        clearEntries()
        insertEntries(rows)
        saveState(state)
    }
}

@Dao
interface OverrideDao {
    @Query("SELECT * FROM classification_overrides")
    fun all(): Flow<List<ClassificationOverride>>

    @Upsert
    suspend fun upsert(o: ClassificationOverride)

    @Query("DELETE FROM classification_overrides WHERE uri = :uri")
    suspend fun remove(uri: String)
}
