package com.imadlabs.iplayer.data.settings

import com.imadlabs.iplayer.ui.theme.AccentColor

enum class ThemeMode(val label: String) { System("Follow system"), Dark("Dark"), Light("Light") }

enum class DecoderMode(val label: String, val description: String) {
    Auto("Automatic", "Hardware decoding when the device supports the stream, software otherwise."),
    Hardware("Hardware", "Force the device's hardware decoders (MediaCodec). Fastest, but some profiles fail."),
    Software("Software", "Decode on the CPU with FFmpeg. Most compatible, uses more battery."),
}

enum class AspectMode(val label: String) {
    Fit("Fit"), Crop("Fill & crop"), Stretch("Stretch"), Original("Original size"),
    Ratio16x9("16:9"), Ratio4x3("4:3"), Ratio235("2.35:1"),
}

enum class EndAction(val label: String) {
    Next("Play next in queue"), Stop("Stop"), RepeatOne("Repeat this file"), Close("Close player")
}

enum class OrientationPref(val label: String) {
    Auto("Automatic (by video)"), Sensor("Follow sensor"), Landscape("Landscape"), Portrait("Portrait")
}

enum class LibraryLayout { Grid, List }

enum class SortOrder(val label: String) {
    DateAddedDesc("Newest first"), DateAddedAsc("Oldest first"),
    NameAsc("Name A–Z"), NameDesc("Name Z–A"),
    DurationDesc("Longest first"), DurationAsc("Shortest first"),
    SizeDesc("Largest first"), SizeAsc("Smallest first"),
}

enum class ReplayGainMode(val vlcValue: String, val label: String) {
    Off("none", "Off"), Track("track", "Track gain"), Album("album", "Album gain")
}

/** VLC "stereo-mode" values (aout channel variable). */
enum class StereoMode(val vlcValue: Int, val label: String) {
    Default(0, "Original"), Stereo(1, "Stereo"), Reverse(2, "Reverse stereo"),
    Left(3, "Left channel only"), Right(4, "Right channel only"), Mono(7, "Mono downmix"),
}

enum class SubtitleColor(val label: String, val rgb: Int) {
    White("White", 0xFFFFFF), Yellow("Yellow", 0xFFFF00), Mint("Cyber Mint", 0x00F5A0),
    Cyan("Cyan", 0x38BDF8), Gray("Light gray", 0xC0C0C0),
}

enum class SubtitleSize(val label: String, val vlcRelFontSize: Int) {
    Small("Small", 20), Normal("Normal", 16), Large("Large", 12), Huge("Huge", 10)
}

enum class SubtitleOutline(val label: String, val vlcValue: Int) {
    None("None", 0), Thin("Thin", 2), Normal("Normal", 4), Thick("Thick", 6)
}

enum class Density(val label: String) { Comfortable("Comfortable"), Compact("Compact") }

data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetIndex: Int = 0,
    val preamp: Float = 12f,
    val bands: List<Float> = emptyList(),
    val customName: String? = null,
)

data class AppSettings(
    // Playback
    val defaultSpeed: Float = 1f,
    val resetSpeedOnNewMedia: Boolean = true,
    val seekIntervalSec: Int = 10,
    val autoPlayNext: Boolean = true,
    val resumePlayback: Boolean = true,
    val endAction: EndAction = EndAction.Next,
    val decoderMode: DecoderMode = DecoderMode.Auto,
    val decoderFallback: Boolean = true,
    val autoPip: Boolean = true,
    val backgroundVideoAudio: Boolean = false,
    // Video
    val defaultAspect: AspectMode = AspectMode.Fit,
    val orientation: OrientationPref = OrientationPref.Auto,
    val brightnessGesture: Boolean = true,
    val volumeGesture: Boolean = true,
    val seekGesture: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val pinchZoom: Boolean = true,
    val longPressSpeed: Boolean = true,
    val longPressSpeedValue: Float = 2f,
    val twoFingerLock: Boolean = false,
    val controlsTimeoutSec: Int = 4,
    val showRemainingTime: Boolean = false,
    val useTextureView: Boolean = true,
    val seekPreview: Boolean = true,
    // Audio
    val equalizer: EqualizerSettings = EqualizerSettings(),
    val audioDelayMs: Long = 0,
    val replayGain: ReplayGainMode = ReplayGainMode.Off,
    val replayGainPreamp: Float = 0f,
    val volumeNormalizer: Boolean = false,
    val stereoWiden: Boolean = false,
    val stereoMode: StereoMode = StereoMode.Default,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val resumeOnFocusGain: Boolean = true,
    val digitalPassthrough: Boolean = false,
    // Subtitles
    val subtitlesEnabled: Boolean = true,
    val preferredSubtitleLanguage: String = "",
    val preferredAudioLanguage: String = "",
    val subtitleSize: SubtitleSize = SubtitleSize.Normal,
    val subtitleColor: SubtitleColor = SubtitleColor.White,
    val subtitleBold: Boolean = false,
    val subtitleOutline: SubtitleOutline = SubtitleOutline.Normal,
    val subtitleShadow: Boolean = true,
    val subtitleBackgroundOpacity: Int = 0,
    val subtitleBottomMargin: Int = 0,
    val subtitleEncoding: String = "",
    val subtitleAutoDetect: Boolean = true,
    // Library
    val videoLayout: LibraryLayout = LibraryLayout.Grid,
    val videoSort: SortOrder = SortOrder.DateAddedDesc,
    val musicSort: SortOrder = SortOrder.NameAsc,
    val showThumbnails: Boolean = true,
    val groupEpisodes: Boolean = true,
    val hideVoiceNotes: Boolean = true,
    // Appearance
    val themeMode: ThemeMode = ThemeMode.Dark,
    val accent: AccentColor = AccentColor.CyberMint,
    val animations: Boolean = true,
    val density: Density = Density.Comfortable,
    // Privacy
    val historyEnabled: Boolean = true,
    // Network
    val networkCachingMs: Int = 1500,
    // Updates
    val autoUpdateCheck: Boolean = true,
)
