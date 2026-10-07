package com.scott.pilltracker.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.scott.pilltracker.R

object AlarmRingtonePlayer {
    private const val TAG = "AlarmRingtonePlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoStopRunnable: Runnable? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var savedAlarmVolume: Int? = null

    var isPlaying: Boolean = false
        private set

    @Synchronized
    fun play(context: Context, durationMillis: Long = 60_000L) {
        stop()

        isPlaying = true
        val appContext = context.applicationContext
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val am = audioManager

        // 1. Audio Focus: Request transient audio focus for alarm
        try {
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener { focusChange ->
                            Log.d(TAG, "Audio focus changed: $focusChange")
                        }
                        .build()
                    audioFocusRequest = focusReq
                    val res = am.requestAudioFocus(focusReq)
                    Log.d(TAG, "Audio focus requested: result=$res")
                } else {
                    @Suppress("DEPRECATION")
                    am.requestAudioFocus(
                        null,
                        AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error requesting audio focus: ${e.message}")
        }

        // 2. Alarm Stream Volume Safeguard: Ensure volume is audible
        try {
            if (am != null) {
                val currentVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                Log.d(TAG, "Current STREAM_ALARM volume: $currentVol / $maxVol")

                // If volume is muted (0) or very low, temporarily raise to ~70%
                if (currentVol < (maxVol * 0.4).toInt()) {
                    savedAlarmVolume = currentVol
                    val targetVol = (maxVol * 0.75).toInt().coerceAtLeast(1)
                    am.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)
                    Log.d(TAG, "Alarm volume boosted from $currentVol to $targetVol for pill reminder.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking/adjusting stream volume: ${e.message}")
        }

        // 3. Play Alarm Sound via USAGE_ALARM stream with robust multi-tier fallback
        var playbackStarted = false

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Tier 1: Try playing actual system default alarm or ringtone URI
        try {
            val systemUri: Uri? = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_NOTIFICATION)

            if (systemUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(appContext, systemUri)
                    setAudioAttributes(audioAttributes)
                    setVolume(1.0f, 1.0f)
                    isLooping = true
                    prepare()
                    start()
                }
                playbackStarted = true
                Log.d(TAG, "MediaPlayer started successfully with system ringtone URI: $systemUri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer failed with system URI (${e.message}), trying bundled chime...")
            try {
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }

        // Tier 2: Bundled high-quality chime (R.raw.pill_alarm)
        if (!playbackStarted) {
            try {
                mediaPlayer = MediaPlayer.create(appContext, R.raw.pill_alarm)?.apply {
                    setAudioAttributes(audioAttributes)
                    setVolume(1.0f, 1.0f)
                    isLooping = true
                    start()
                }
                if (mediaPlayer != null) {
                    playbackStarted = true
                    Log.d(TAG, "MediaPlayer started successfully with bundled R.raw.pill_alarm.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer failed with bundled R.raw.pill_alarm: ${e.message}")
                try {
                    mediaPlayer?.release()
                } catch (_: Exception) {}
                mediaPlayer = null
            }
        }

        // Tier 3: RingtoneManager fallback
        if (!playbackStarted) {
            try {
                val fallbackUri = RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (fallbackUri != null) {
                    ringtone = RingtoneManager.getRingtone(appContext, fallbackUri)?.apply {
                        this.audioAttributes = audioAttributes
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            isLooping = true
                        }
                        play()
                    }
                    if (ringtone != null && ringtone?.isPlaying == true) {
                        playbackStarted = true
                        Log.d(TAG, "Fallback Ringtone started playing.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback ringtone failed: ${e.message}")
            }
        }

        if (!playbackStarted) {
            Log.e(TAG, "CRITICAL: All audio playback tiers failed to start sound!")
        }

        // 4. Start Strong Alarm Vibration
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val pattern = longArrayOf(0, 600, 250, 600, 250, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting alarm vibration: ${e.message}")
        }

        // 5. Auto-stop timer to avoid draining battery indefinitely
        autoStopRunnable = Runnable {
            Log.d(TAG, "Alarm auto-stopped after ${durationMillis}ms")
            stop()
        }
        mainHandler.postDelayed(autoStopRunnable!!, durationMillis)
    }

    @Synchronized
    fun stop() {
        autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
        autoStopRunnable = null

        // Stop and release MediaPlayer
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }

        // Stop Ringtone
        try {
            ringtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Ringtone: ${e.message}")
        } finally {
            ringtone = null
        }

        // Cancel vibration
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping vibrator: ${e.message}")
        } finally {
            vibrator = null
        }

        // Restore alarm volume if we temporarily boosted it
        try {
            savedAlarmVolume?.let { savedVol ->
                audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, savedVol, 0)
                Log.d(TAG, "Restored STREAM_ALARM volume back to $savedVol.")
                savedAlarmVolume = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring alarm volume: ${e.message}")
        }

        // Abandon Audio Focus so other apps (e.g. YouTube) can resume
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { focusReq ->
                    audioManager?.abandonAudioFocusRequest(focusReq)
                    Log.d(TAG, "Audio focus abandoned.")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error abandoning audio focus: ${e.message}")
        } finally {
            audioFocusRequest = null
            audioManager = null
        }

        isPlaying = false
        Log.d(TAG, "Alarm sound and vibration stopped.")
    }
}
