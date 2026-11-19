package com.example.ui

import android.app.Application
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RemotePreferences
import com.example.data.SettingsStore
import com.example.network.ClickAction
import com.example.network.ConnectionState
import com.example.network.MouseButton
import com.example.network.PcHost
import com.example.network.PcRemoteClient
import com.example.voice.VoiceAction
import com.example.voice.VoiceCommandParser
import com.example.voice.VoiceController
import com.example.voice.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

data class RemoteUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val discoveredHosts: List<PcHost> = emptyList(),
    val recentHosts: List<PcHost> = emptyList(),
    val preferences: RemotePreferences = RemotePreferences(),
    val voiceState: VoiceState = VoiceState.Idle,
    val isKeyboardExpanded: Boolean = false,
    val isConnectSheetOpen: Boolean = false,
    val isPreferencesSheetOpen: Boolean = false,
    val isDragging: Boolean = false,
    val isPresentationMode: Boolean = false,
    val isLaserActive: Boolean = false,
    val isBlackScreenActive: Boolean = false,
    val volumeKeysChangeSlides: Boolean = true,
    val liveTypeMode: Boolean = true,
    val keyboardInput: TextFieldValue = TextFieldValue(""),
    val lastActionBanner: String? = null,
    val phoneWifiIp: String? = null,
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val client = PcRemoteClient(application)
    private val settingsStore = SettingsStore(application)
    private val voiceController = VoiceController(application)

    private val _isKeyboardExpanded = MutableStateFlow(false)
    private val _isConnectSheetOpen = MutableStateFlow(false)
    private val _isPreferencesSheetOpen = MutableStateFlow(false)
    private val _isDragging = MutableStateFlow(false)
    private val _isPresentationMode = MutableStateFlow(false)
    private val _isLaserActive = MutableStateFlow(false)
    private val _isBlackScreenActive = MutableStateFlow(false)
    private val _volumeKeysChangeSlides = MutableStateFlow(true)
    private val _liveTypeMode = MutableStateFlow(true)
    private val _keyboardInput = MutableStateFlow(TextFieldValue(""))
    private val _lastActionBanner = MutableStateFlow<String?>(null)

    private var committedLiveText = ""
    private var subpixelX = 0f
    private var subpixelY = 0f
    private var smoothedDx = 0f
    private var smoothedDy = 0f
    private var scrollAccumX = 0f
    private var scrollAccumY = 0f
    private var bannerJob: Job? = null

    private val uiFlagsFlow = combine(
        _isKeyboardExpanded,
        _isConnectSheetOpen,
        _isPreferencesSheetOpen,
        _isDragging,
        _liveTypeMode,
    ) { keyboard, connectSheet, prefsSheet, dragging, liveType ->
        UiFlags(keyboard, connectSheet, prefsSheet, dragging, liveType)
    }

    private val presentationFlagsFlow = combine(
        _isPresentationMode,
        _isLaserActive,
        _isBlackScreenActive,
        _volumeKeysChangeSlides,
    ) { presMode, laser, blackScreen, volSlides ->
        PresentationFlags(presMode, laser, blackScreen, volSlides)
    }

    val uiState: StateFlow<RemoteUiState> = combine(
        client.connectionState,
        client.discoveredHosts,
        settingsStore.settingsFlow,
        voiceController.voiceState,
        uiFlagsFlow,
        presentationFlagsFlow,
        _keyboardInput,
        _lastActionBanner,
        client.phoneWifiIp,
    ) { values ->
        val connState = values[0] as ConnectionState
        @Suppress("UNCHECKED_CAST")
        val discovered = values[1] as List<PcHost>
        val stored = values[2] as com.example.data.StoredSettings
        val voice = values[3] as VoiceState
        val flags = values[4] as UiFlags
        val presFlags = values[5] as PresentationFlags
        val keyboardVal = values[6] as TextFieldValue
        val banner = values[7] as String?
        val phoneIp = values[8] as String?

        RemoteUiState(
            connectionState = connState,
            discoveredHosts = discovered,
            recentHosts = stored.recentHosts,
            preferences = stored.preferences,
            voiceState = voice,
            isKeyboardExpanded = flags.isKeyboardExpanded,
            isConnectSheetOpen = flags.isConnectSheetOpen,
            isPreferencesSheetOpen = flags.isPreferencesSheetOpen,
            isDragging = flags.isDragging,
            isPresentationMode = presFlags.isPresentationMode,
            isLaserActive = presFlags.isLaserActive,
            isBlackScreenActive = presFlags.isBlackScreenActive,
            volumeKeysChangeSlides = presFlags.volumeKeysChangeSlides,
            liveTypeMode = flags.liveTypeMode,
            keyboardInput = keyboardVal,
            lastActionBanner = banner,
            phoneWifiIp = phoneIp,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RemoteUiState(),
    )

    fun openConnectSheet() {
        _isConnectSheetOpen.value = true
        client.discoverHosts()
    }

    fun closeConnectSheet() {
        _isConnectSheetOpen.value = false
        client.clearError()
    }

    fun openPreferencesSheet() {
        _isPreferencesSheetOpen.value = true
    }

    fun closePreferencesSheet() {
        _isPreferencesSheetOpen.value = false
    }

    fun scanWifiHosts() {
        client.discoverHosts()
    }

    fun connectToHost(host: PcHost) {
        client.connect(
            host = host,
            onConnected = { connectedHost ->
                viewModelScope.launch {
                    settingsStore.saveRecentHost(connectedHost)
                }
                _isConnectSheetOpen.value = false
                showBanner("Connected to ${connectedHost.displayLabel}")
            },
            onError = {
                _isConnectSheetOpen.value = true
            },
        )
    }

    fun connectManual(ip: String, tcpPort: Int = PcHost.DEFAULT_TCP_PORT, udpPort: Int = PcHost.DEFAULT_UDP_PORT) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) return
        connectToHost(
            PcHost(
                name = cleanIp,
                ipAddress = cleanIp,
                tcpPort = tcpPort,
                udpPort = udpPort,
            ),
        )
    }

    fun disconnect() {
        setLaserPointerActive(false)
        setDragHold(false)
        client.disconnect()
        showBanner("Disconnected")
    }

    fun onTrackpadMove(deltaX: Float, deltaY: Float) {
        if (uiState.value.connectionState !is ConnectionState.Connected) return
        val baseSens = uiState.value.preferences.pointerSensitivity
        val distance = hypot(deltaX, deltaY)
        // Low-pass filter subtle micro-jitter on slow movements while keeping fast flicks crisp
        val alpha = if (distance < 3.5f) 0.68f else 1f
        smoothedDx = smoothedDx + (deltaX - smoothedDx) * alpha
        smoothedDy = smoothedDy + (deltaY - smoothedDy) * alpha

        val smoothDist = hypot(smoothedDx, smoothedDy)
        val accel = 1f + (smoothDist / 24f).pow(1.22f).coerceAtMost(1.35f)
        subpixelX += smoothedDx * baseSens * accel
        subpixelY += smoothedDy * baseSens * accel

        val sendX = subpixelX.toInt()
        val sendY = subpixelY.toInt()
        if (sendX != 0 || sendY != 0) {
            subpixelX -= sendX
            subpixelY -= sendY
            client.sendMove(sendX, sendY)
        }
    }

    fun onTrackpadScroll(deltaX: Float, deltaY: Float) {
        if (uiState.value.connectionState !is ConnectionState.Connected) return
        val prefs = uiState.value.preferences
        val direction = if (prefs.naturalScrolling) 1f else -1f
        val gain = prefs.scrollSensitivity * 2.2f

        scrollAccumX += deltaX * gain * (-direction)
        scrollAccumY += deltaY * gain * direction

        val vx = scrollAccumX.roundToInt()
        val vy = scrollAccumY.roundToInt()
        if (abs(vx) >= 4 || abs(vy) >= 4) {
            val sendVx = if (abs(vx) >= 4) vx else 0
            val sendVy = if (abs(vy) >= 4) vy else 0
            scrollAccumX -= sendVx
            scrollAccumY -= sendVy
            client.sendScroll(sendVx, sendVy)
        }
    }

    fun resetAccumulators() {
        subpixelX = 0f
        subpixelY = 0f
        smoothedDx = 0f
        smoothedDy = 0f
        scrollAccumX = 0f
        scrollAccumY = 0f
    }

    fun sendClick(button: MouseButton, action: ClickAction = ClickAction.TAP) {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        client.sendClick(button, action)
    }

    fun setDragHold(active: Boolean) {
        if (_isDragging.value == active) return
        if (active && uiState.value.connectionState !is ConnectionState.Connected) return
        _isDragging.value = active
        if (uiState.value.connectionState is ConnectionState.Connected) {
            client.sendClick(
                MouseButton.LEFT,
                if (active) ClickAction.DOWN else ClickAction.UP,
            )
        }
    }

    fun setPresentationMode(enabled: Boolean) {
        if (_isPresentationMode.value == enabled) return
        if (!enabled) {
            setLaserPointerActive(false)
        }
        _isPresentationMode.value = enabled
        resetAccumulators()
    }

    fun toggleVolumeKeysChangeSlides() {
        val next = !_volumeKeysChangeSlides.value
        _volumeKeysChangeSlides.value = next
        showBanner(if (next) "Volume keys now change slides" else "Volume keys disabled for slides")
    }

    fun isHardwareSlideControlActive(): Boolean =
        _isPresentationMode.value &&
            !_isConnectSheetOpen.value &&
            !_isPreferencesSheetOpen.value

    fun onHardwareVolumeKey(isVolumeUp: Boolean) {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        if (isVolumeUp) {
            nextSlide()
        } else {
            previousSlide()
        }
    }

    fun nextSlide() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        client.sendKey("RIGHT")
        showBanner("Next slide")
    }

    fun previousSlide() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        client.sendKey("LEFT")
        showBanner("Previous")
    }

    fun startPresentation() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        _isBlackScreenActive.value = false
        client.sendKey("F5")
        showBanner("Slideshow started")
    }

    fun endPresentation() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        setLaserPointerActive(false)
        _isBlackScreenActive.value = false
        client.sendKey("ESC")
        showBanner("Slideshow ended")
    }

    fun toggleBlackScreen() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        val next = !_isBlackScreenActive.value
        _isBlackScreenActive.value = next
        client.sendKey("B")
        showBanner(if (next) "Black screen on" else "Black screen off")
    }

    fun setLaserPointerActive(active: Boolean) {
        if (_isLaserActive.value == active) return
        if (active && uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        _isLaserActive.value = active
        if (uiState.value.connectionState is ConnectionState.Connected) {
            client.sendLaser(active)
            if (!active) {
                resetAccumulators()
            }
        }
    }

    fun toggleKeyboard() {
        _isKeyboardExpanded.value = !_isKeyboardExpanded.value
    }

    fun toggleLiveTypeMode() {
        val next = !_liveTypeMode.value
        _liveTypeMode.value = next
        committedLiveText = _keyboardInput.value.text
        showBanner(if (next) "Live typing enabled" else "Batch send mode")
    }

    fun onKeyboardInputChange(newValue: TextFieldValue) {
        _keyboardInput.value = newValue
        if (!_liveTypeMode.value) return
        if (uiState.value.connectionState !is ConnectionState.Connected) return

        // Wait until IME finishes composing word (prevents duplicate autocorrect tokens)
        if (newValue.composition != null) return

        val nextText = newValue.text
        if (nextText == committedLiveText) return

        val commonPrefix = commonPrefixLength(committedLiveText, nextText)
        val deletedCount = committedLiveText.length - commonPrefix
        val addedText = nextText.substring(commonPrefix)

        repeat(deletedCount) {
            client.sendKey("BACKSPACE")
        }
        if (addedText.isNotEmpty()) {
            client.sendText(addedText)
        }
        committedLiveText = nextText
    }

    fun sendKeyboardDraftNow() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        val current = _keyboardInput.value.text
        if (_liveTypeMode.value) {
            val commonPrefix = commonPrefixLength(committedLiveText, current)
            val deletedCount = committedLiveText.length - commonPrefix
            val addedText = current.substring(commonPrefix)
            repeat(deletedCount) { client.sendKey("BACKSPACE") }
            if (addedText.isNotEmpty()) client.sendText(addedText)
            client.sendKey("ENTER")
            _keyboardInput.value = TextFieldValue("")
            committedLiveText = ""
        } else if (current.isNotEmpty()) {
            client.sendText(current)
            _keyboardInput.value = TextFieldValue("")
            committedLiveText = ""
            showBanner("Sent text to PC")
        } else {
            client.sendKey("ENTER")
        }
    }

    fun clearKeyboardDraft() {
        _keyboardInput.value = TextFieldValue("")
        committedLiveText = ""
    }

    fun sendSpecialKey(key: String, modifiers: List<String> = emptyList(), bannerLabel: String? = null) {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        client.sendKey(key, modifiers)
        if (bannerLabel != null) {
            showBanner(bannerLabel)
        }
    }

    fun isSpeechAvailable(): Boolean = voiceController.isAvailable()

    fun startVoiceRecognition() {
        if (uiState.value.connectionState !is ConnectionState.Connected) {
            openConnectSheet()
            return
        }
        voiceController.startListening { transcript ->
            handleVoiceTranscript(transcript)
        }
    }

    fun stopVoiceRecognition() {
        voiceController.stopListening()
    }

    fun cancelVoiceRecognition() {
        voiceController.cancelListening()
    }

    fun dismissVoiceOverlay() {
        voiceController.resetState()
    }

    private fun handleVoiceTranscript(transcript: String) {
        val autoCommands = uiState.value.preferences.voiceAutoCommands
        val action = VoiceCommandParser.parse(transcript, autoCommands)
        when (action) {
            is VoiceAction.Click -> client.sendClick(action.button, action.action)
            is VoiceAction.Scroll -> client.sendScroll(0, action.verticalAmount)
            is VoiceAction.KeyCombo -> client.sendKey(action.key, action.modifiers)
            is VoiceAction.TypeText -> {
                if (action.text.isNotEmpty()) {
                    client.sendText(action.text + " ")
                }
            }
        }
        voiceController.publishResult(transcript, action.label)
        showBanner("Voice: ${action.label}")
    }

    fun updatePreferences(transform: (RemotePreferences) -> RemotePreferences) {
        viewModelScope.launch {
            settingsStore.updatePreferences(transform)
        }
    }

    fun showBanner(message: String) {
        bannerJob?.cancel()
        _lastActionBanner.value = message
        bannerJob = viewModelScope.launch {
            delay(2400L)
            _lastActionBanner.value = null
        }
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val minLen = minOf(a.length, b.length)
        var i = 0
        while (i < minLen && a[i] == b[i]) {
            i++
        }
        return i
    }

    override fun onCleared() {
        super.onCleared()
        setLaserPointerActive(false)
        voiceController.destroy()
        client.shutdown()
    }

    private data class UiFlags(
        val isKeyboardExpanded: Boolean,
        val isConnectSheetOpen: Boolean,
        val isPreferencesSheetOpen: Boolean,
        val isDragging: Boolean,
        val liveTypeMode: Boolean,
    )

    private data class PresentationFlags(
        val isPresentationMode: Boolean,
        val isLaserActive: Boolean,
        val isBlackScreenActive: Boolean,
        val volumeKeysChangeSlides: Boolean,
    )
}
