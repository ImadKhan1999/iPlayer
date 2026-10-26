package com.imadlabs.iplayer.data.media

import com.imadlabs.iplayer.data.db.AppDatabase
import com.imadlabs.iplayer.data.db.ClassificationOverride
import com.imadlabs.iplayer.data.db.ExcludedFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LibrarySnapshot(
    val loaded: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val audio: List<AudioItem> = emptyList(),
) {
    val music: List<AudioItem> get() = audio.filter { it.isMusic || !it.isRecording }
}

/** Library-wide audio filters from Settings › Library. */
data class AudioFilter(val hideVoiceNotes: Boolean, val hideShortAudio: Boolean) {
    fun hides(folder: String, durationMs: Long): Boolean =
        (hideVoiceNotes && VoiceNotes.isVoiceNoteFolder(folder)) ||
            (hideShortAudio && durationMs in 1 until SHORT_AUDIO_MS)

    companion object {
        /** Clips under this length (ringtones, chat audio, notification sounds) are hidden. Unknown length is kept. */
        const val SHORT_AUDIO_MS = 30_000L
    }
}

/** Folders where messaging apps keep voice messages and call recordings rather than music. */
object VoiceNotes {
    private val segments = setOf("whatsapp voice notes", "whatsapp business voice notes", "wa call recordings")

    fun isVoiceNoteFolder(folder: String): Boolean =
        folder.split('/').any { it.lowercase() in segments }
}

/** Library facade: MediaStore index + the user's exclusions, refreshed only on MediaStore changes. */
@OptIn(FlowPreview::class)
class LibraryRepository(
    private val source: MediaStoreSource,
    private val audioFilter: Flow<AudioFilter>,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val raw = MutableStateFlow(LibrarySnapshot())
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val mutex = Mutex()
    private var observeJob: Job? = null

    val excludedFolders: StateFlow<List<ExcludedFolder>> =
        db.excludedFolders().all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val overrides: StateFlow<List<ClassificationOverride>> =
        db.overrides().all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<LibrarySnapshot> = combine(raw, excludedFolders, audioFilter) { snap, excluded, filter ->
        val paths = excluded.map { it.path.trimEnd('/') }
        fun hidden(folder: String) = paths.any { folder == it || folder.startsWith("$it/") }
        snap.copy(videos = snap.videos.filterNot { hidden(it.folder) },
            audio = snap.audio.filterNot { hidden(it.folder) || filter.hides(it.folder, it.durationMs) })
    }.stateIn(scope, SharingStarted.Eagerly, LibrarySnapshot())

    val albums: StateFlow<List<Album>> = library.map { lib ->
        lib.music.groupBy { it.albumId }.map { (id, tracks) ->
            val sorted = tracks.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.title.lowercase() }))
            Album(id, sorted.first().album, sorted.first().albumArtist ?: sorted.first().artist,
                sorted.maxOf { it.year }, sorted)
        }.sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val artists: StateFlow<List<Artist>> = combine(library, albums) { lib, albums ->
        lib.music.groupBy { it.albumArtist ?: it.artist }.map { (name, tracks) ->
            Artist(name, albums.filter { it.artist == name }, tracks.sortedBy { it.title.lowercase() })
        }.sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val genres: StateFlow<List<Genre>> = library.map { lib ->
        lib.music.groupBy { it.genre ?: "Unknown genre" }
            .map { (g, t) -> Genre(g, t.sortedBy { it.title.lowercase() }) }
            .sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val folders: StateFlow<List<MediaFolder>> = library.map { lib ->
        val v = lib.videos.groupBy { it.folder }
        val a = lib.audio.groupBy { it.folder }
        (v.keys + a.keys).map { path ->
            MediaFolder(path, path.substringAfterLast('/').ifBlank { path }, v[path].orEmpty(), a[path].orEmpty())
        }.sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    val shows: StateFlow<List<ShowGroup>> = combine(library, overrides) { lib, ov ->
        val byUri = ov.associateBy { it.uri }
        lib.videos.mapNotNull { v ->
            val o = byUri[v.uri.toString()]
            when {
                o != null && !o.isEpisode -> null
                o != null && o.showName != null && o.season != null && o.episode != null ->
                    EpisodeParser.Episode(o.showName, o.season, o.episode) to v
                else -> EpisodeParser.parse(v.displayName, v.folder)?.let { it to v }
            }
        }.groupBy { EpisodeParser.groupKey(it.first) }
            .map { (_, eps) ->
                ShowGroup(eps.first().first.show, eps.groupBy { it.first.season }.toSortedMap()
                    .mapValues { (_, list) -> list.sortedBy { it.first.episode } })
            }
            .filter { it.episodeCount >= 2 }
            .sortedBy { it.name.lowercase() }
    }.stateIn(scope, SharingStarted.Lazily, emptyList())

    /** Duplicate file names across folders, for the "Duplicates" filter. */
    val duplicateNames: StateFlow<Set<String>> = library.map { lib ->
        lib.videos.groupBy { it.displayName.lowercase() }.filterValues { it.size > 1 }.keys
    }.stateIn(scope, SharingStarted.Lazily, emptySet())

    /** Call once storage permission is available. Safe to call repeatedly. */
    fun start() {
        if (observeJob == null) {
            observeJob = source.changes().debounce(1500).onEach { refresh() }.launchIn(scope)
        }
        scope.launch { refresh() }
    }

    suspend fun refresh() = mutex.withLock {
        _scanning.value = true
        try {
            val videos = source.queryVideos()
            val audio = source.queryAudio()
            raw.value = LibrarySnapshot(true, videos, audio)
        } finally {
            _scanning.value = false
        }
    }

    suspend fun exclude(path: String) = db.excludedFolders().add(ExcludedFolder(path, System.currentTimeMillis()))
    suspend fun include(path: String) = db.excludedFolders().remove(path)
    suspend fun setOverride(o: ClassificationOverride) = db.overrides().upsert(o)
    suspend fun clearOverride(uri: String) = db.overrides().remove(uri)

    fun findVideo(uri: String): VideoItem? = library.value.videos.firstOrNull { it.uri.toString() == uri }
    fun findAudio(uri: String): AudioItem? = library.value.audio.firstOrNull { it.uri.toString() == uri }
}
