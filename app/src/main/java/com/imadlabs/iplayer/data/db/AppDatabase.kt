package com.imadlabs.iplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun protocolToString(p: NetworkProtocol): String = p.name
    @TypeConverter fun stringToProtocol(s: String): NetworkProtocol =
        NetworkProtocol.entries.firstOrNull { it.name == s } ?: NetworkProtocol.Smb
}

@Database(
    entities = [
        PlaybackRecord::class, Favorite::class, Playlist::class, PlaylistEntry::class,
        NetworkSource::class, ExcludedFolder::class, QueueEntryRow::class, QueueStateRow::class,
        ClassificationOverride::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackRecords(): PlaybackRecordDao
    abstract fun favorites(): FavoriteDao
    abstract fun playlists(): PlaylistDao
    abstract fun networkSources(): NetworkSourceDao
    abstract fun excludedFolders(): ExcludedFolderDao
    abstract fun queue(): QueueDao
    abstract fun overrides(): OverrideDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "iplayer.db").build()
    }
}
