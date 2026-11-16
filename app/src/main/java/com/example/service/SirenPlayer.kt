package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class SirenPlayer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun start() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        startSosVibration()
        playJob = scope.launch {
            val sampleRate = 22050
            val bufferSamples = sampleRate / 2
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferSamples * 2)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Exception) {
                _isPlaying.value = false
                return@launch
            }

            try {
                track.play()
                val pcm = ShortArray(bufferSamples)
                var phase = 0.0
                var tick = 0L
                while (isActive && _isPlaying.value) {
                    for (i in pcm.indices) {
                        val t = (tick + i).toDouble() / sampleRate
                        // Sweep between 850 Hz and 1650 Hz every 0.8s for high audibility
                        val sweep = 0.5 * (1.0 + sin(2.0 * PI * 1.25 * t))
                        val freq = 850.0 + sweep * 800.0
                        phase += 2.0 * PI * freq / sampleRate
                        if (phase > 2.0 * PI) phase -= 2.0 * PI
                        pcm[i] = (sin(phase) * Short.MAX_VALUE * 0.9).toInt().toShort()
                    }
                    tick += pcm.size
                    track.write(pcm, 0, pcm.size)
                }
            } catch (_: Exception) {
                // Gracefully stop if audio hardware unavailable (e.g., Robolectric/headless)
            } finally {
                runCatching {
                    track.stop()
                    track.release()
                }
            }
        }
    }

    fun stop() {
        _isPlaying.value = false
        playJob?.cancel()
        playJob = null
        stopVibration()
    }

    @Suppress("DEPRECATION")
    private fun startSosVibration() {
        try {
            val pattern = longArrayOf(
                0,
                150, 100, 150, 100, 150, 250, // S
                400, 100, 400, 100, 400, 250, // O
                150, 100, 150, 100, 150, 600  // S
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    vibrator?.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun stopVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.cancel()
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
        } catch (_: Exception) {
        }
    }
}
