package com.example.davidapp.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Presentation only. Clearing this overlay never removes the conversation history. */
internal class DavidLiveSubtitleLifecycle(private val scope: CoroutineScope) {
    private val lock = Any()
    private var revision = 0L
    private var dismissal: Job? = null
    private var davidTurnActive = false
    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()
    private val _speaker = MutableStateFlow(DavidLiveSpeaker.NONE)
    val speaker = _speaker.asStateFlow()

    fun show(text: String, speaker: DavidLiveSpeaker) = synchronized(lock) {
        cancelDismissal()
        davidTurnActive = speaker == DavidLiveSpeaker.DAVID
        _speaker.value = speaker
        _text.value = text
    }

    /** Audio may arrive before its transcript. Never leave the accepted user turn on screen. */
    fun beginDavidTurn() = synchronized(lock) {
        cancelDismissal()
        if (!davidTurnActive) _text.value = ""
        davidTurnActive = true
        _speaker.value = DavidLiveSpeaker.DAVID
    }

    fun acceptUserTurn() = synchronized(lock) {
        if (_speaker.value == DavidLiveSpeaker.USER) clear()
    }

    /** Late playback observers must not resurrect a completed or disconnected turn. */
    fun updateDavidText(text: String) = synchronized(lock) {
        if (davidTurnActive) _text.value = text
    }

    /** Called only after server turnComplete AND the local audio buffer have drained. */
    fun completeDavidTurn() = synchronized(lock) {
        if (_speaker.value != DavidLiveSpeaker.DAVID || _text.value.isBlank() || dismissal != null) return@synchronized
        davidTurnActive = false
        val completedRevision = revision
        dismissal = scope.launch {
            delay(2_500L)
            synchronized(lock) {
                if (revision == completedRevision) {
                    _text.value = ""
                    dismissal = null
                }
            }
        }
    }

    fun clear() = synchronized(lock) {
        cancelDismissal()
        davidTurnActive = false
        _text.value = ""
        _speaker.value = DavidLiveSpeaker.NONE
    }

    private fun cancelDismissal() {
        revision++
        dismissal?.cancel()
        dismissal = null
    }
}
