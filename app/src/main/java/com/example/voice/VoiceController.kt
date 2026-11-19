package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

sealed interface VoiceState {
    data object Idle : VoiceState
    data class Listening(
        val partialText: String = "",
        val rmsDb: Float = 0f,
    ) : VoiceState

    data class Result(
        val transcript: String,
        val actionLabel: String,
    ) : VoiceState

    data class Error(val message: String) : VoiceState
}

class VoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening(onTranscript: (String) -> Unit) {
        mainHandler.post {
            if (!isAvailable()) {
                _voiceState.value = VoiceState.Error("Speech recognition unavailable on this device.")
                return@post
            }

            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer = sr

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceState.Listening()
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    val current = _voiceState.value
                    if (current is VoiceState.Listening) {
                        _voiceState.value = current.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
                    }
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Speech service offline or timed out"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try speaking clearly"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy — try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        else -> "Voice recognition stopped (code $error)"
                    }
                    _voiceState.value = VoiceState.Error(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull()?.trim().orEmpty()
                    if (best.isEmpty()) {
                        _voiceState.value = VoiceState.Error("No speech recognized")
                    } else {
                        onTranscript(best)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    val current = _voiceState.value
                    if (current is VoiceState.Listening && partial.isNotBlank()) {
                        _voiceState.value = current.copy(partialText = partial)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            _voiceState.value = VoiceState.Listening()
            sr.startListening(intent)
        }
    }

    fun stopListening() {
        mainHandler.post {
            recognizer?.stopListening()
        }
    }

    fun cancelListening() {
        mainHandler.post {
            recognizer?.cancel()
            _voiceState.value = VoiceState.Idle
        }
    }

    fun publishResult(transcript: String, actionLabel: String) {
        _voiceState.value = VoiceState.Result(transcript, actionLabel)
    }

    fun resetState() {
        _voiceState.value = VoiceState.Idle
    }

    fun destroy() {
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
        }
    }
}
