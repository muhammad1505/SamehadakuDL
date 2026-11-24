package app.samehadaku.dl.presentation.player

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import timber.log.Timber

/**
 * Equalizer presets and a loudness boost for the internal player.
 *
 * Both effects follow ExoPlayer's audio session id, which is only known after
 * playback starts, so [attach] is called from the player listener rather than at
 * construction time.
 */
internal enum class EqPreset(
    val label: String,
    val badge: String,
    val description: String,
) {
    NORMAL("Normal", "NORM", "Suara asli tanpa warna tambahan"),
    BASS("Cinema Bass", "BASS", "Bass dalam, cocok untuk aksi dan film"),
    VOCAL("Vocal Boost", "VOCL", "Dialog dan vokal lebih maju"),
    NIGHT("Night Mode", "NITE", "Suara rata dan lebih tenang untuk malam"),
}

internal class PlayerAudioEffects {

    private var equalizer: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var sessionId = C.AUDIO_SESSION_ID_UNSET
    private var currentPreset = EqPreset.NORMAL
    private var currentBoost = 100

    /** True once the platform provided an equalizer for the current session. */
    val hasEqualizer: Boolean get() = equalizer != null

    /** Attaches the effects to ExoPlayer's audio session, ignoring repeats. */
    fun attach(id: Int) {
        if (id == C.AUDIO_SESSION_ID_UNSET || id == 0 || id == sessionId) return
        release()
        sessionId = id
        equalizer = runCatching {
            Equalizer(0, id).apply { enabled = true }
        }.onFailure { Timber.w(it, "Equalizer tidak tersedia") }.getOrNull()
        loudness = runCatching {
            LoudnessEnhancer(id).apply { enabled = true }
        }.onFailure { Timber.w(it, "Loudness enhancer tidak tersedia") }.getOrNull()
        // The session usually appears after the user already picked a preset,
        // so reapply the current selection to the fresh effect instances.
        applyPreset(currentPreset)
        applyBoost(currentBoost)
    }

    fun apply(preset: EqPreset, boostPercent: Int) {
        currentPreset = preset
        currentBoost = boostPercent
        applyPreset(preset)
        applyBoost(boostPercent)
    }

    private fun applyPreset(preset: EqPreset) {
        val eq = equalizer ?: return
        runCatching {
            val bands = eq.numberOfBands.toInt()
            if (bands <= 0) return
            val range = eq.bandLevelRange
            val max = range[1].toFloat()
            val min = range[0].toFloat()
            for (band in 0 until bands) {
                val position = if (bands == 1) 0.5f else band.toFloat() / (bands - 1)
                val factor = when (preset) {
                    // Flat response.
                    EqPreset.NORMAL -> 0f
                    // Lift the low end, keep a little sparkle on top.
                    EqPreset.BASS -> when {
                        position < 0.34f -> 0.75f
                        position > 0.72f -> 0.25f
                        else -> -0.15f
                    }
                    // Pull the mids forward and trim the extremes.
                    EqPreset.VOCAL -> when {
                        position in 0.3f..0.7f -> 0.6f
                        else -> -0.2f
                    }
                    // Soften the highs and lift the lows slightly so late-night
                    // listening stays intelligible at low volume.
                    EqPreset.NIGHT -> when {
                        position > 0.7f -> -0.45f
                        position < 0.3f -> 0.2f
                        else -> 0.05f
                    }
                }
                val level = (max * factor).coerceIn(min, max).toInt().toShort()
                eq.setBandLevel(band.toShort(), level)
            }
        }.onFailure { Timber.w(it, "Preset equalizer gagal diterapkan") }
    }

    private fun applyBoost(boostPercent: Int) {
        val enhancer = loudness ?: return
        runCatching {
            val percent = boostPercent.coerceIn(100, 150)
            // 100% is untouched audio; 150% adds about 10 dB, which is loud
            // enough to help quiet dialogue without clipping immediately.
            val gainMb = (percent - 100) * 20
            enhancer.setTargetGain(gainMb)
            enhancer.enabled = percent > 100
        }.onFailure { Timber.w(it, "Loudness boost gagal diterapkan") }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { loudness?.release() }
        equalizer = null
        loudness = null
        sessionId = C.AUDIO_SESSION_ID_UNSET
    }
}
