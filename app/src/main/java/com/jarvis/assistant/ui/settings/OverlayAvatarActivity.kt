package com.jarvis.assistant.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.jarvis.assistant.JarvisApp
import com.jarvis.assistant.R
import com.jarvis.assistant.automation.session.AvatarState
import com.jarvis.assistant.data.preferences.AvatarSettings
import com.jarvis.assistant.data.preferences.AvatarSettingsDataStore
import com.jarvis.assistant.ui.agent.DynamicOverlayAvatarView
import com.jarvis.assistant.ui.grok.GrokBotSoundManager
import com.jarvis.assistant.util.ThemeManager
import kotlinx.coroutines.launch

class OverlayAvatarActivity : AppCompatActivity() {

    private val preferences get() = JarvisApp.instance.preferences
    private lateinit var dataStore: AvatarSettingsDataStore

    private lateinit var previewAvatarView: DynamicOverlayAvatarView
    private lateinit var spinnerPreviewState: Spinner

    private lateinit var seekBarAvatarSize: SeekBar
    private lateinit var tvAvatarSizePercent: TextView

    private lateinit var spinnerAvatarStyle: Spinner
    private lateinit var spinnerSoundProfile: Spinner
    private lateinit var spinnerAnimationSpeed: Spinner
    private lateinit var spinnerColorTheme: Spinner

    private lateinit var switchParticleAnimation: SwitchCompat
    private lateinit var switchAudioWaveform: SwitchCompat
    private lateinit var switchShowStatusText: SwitchCompat
    private lateinit var switchReduceMotion: SwitchCompat

    private lateinit var switchAudioFeedback: SwitchCompat
    private lateinit var switchHapticFeedback: SwitchCompat

    private lateinit var btnPreviewIdle: MaterialButton
    private lateinit var btnPreviewWorking: MaterialButton
    private lateinit var btnPreviewSpeaking: MaterialButton
    private lateinit var btnPreviewSuccess: MaterialButton

    private lateinit var switchOverlayEnabled: SwitchCompat
    private lateinit var switchDynamicIsland: SwitchCompat
    private lateinit var radioOverlayMode: RadioGroup
    private lateinit var spinnerOverlayEdge: Spinner

    private lateinit var btnSaveSettings: MaterialButton
    private lateinit var btnTopSave: MaterialButton
    private lateinit var tvSaveFeedback: TextView
    private lateinit var avatarGallery: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    private val styleValues = listOf(
        "Droid", "Grok Bot", "Grok Chibi", "Cat", "Ghost", "Alien",
        "Flower", "Star", "Cloud", "Drop", "Blob", "Mech",
        "Pebble", "Pill", "Puddle", "Hexagon", "Triangle", "Circle", "Square",
        "MetaMuse", "Classic", "Neon", "Cosmic", "Minimal", "Pixel"
    )
    private val soundProfileValues = listOf(
        "Cyber Neon", "Cute Chibi", "Deep Mech", "Hyper Pulse", "Soft Ambient", "Retro 8-Bit"
    )
    private val speedValues = listOf("Slow", "Normal", "Fast")
    private val themeValues = listOf("Blue", "Purple", "Green", "Pink", "Orange")
    private val edgeValues = listOf("right", "left", "top", "bottom")
    private val stateOptions = AvatarState.values().toList()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay_avatar)
        ThemeManager.applyDynamicBackground(findViewById(R.id.overlayAvatarRoot))

        dataStore = AvatarSettingsDataStore(this)

        initViews()
        setupListeners()
        loadInitialData()
        setupGallery()
    }

    private fun initViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        previewAvatarView = findViewById(R.id.previewAvatarView)
        spinnerPreviewState = findViewById(R.id.spinnerPreviewState)

        seekBarAvatarSize = findViewById(R.id.seekBarAvatarSize)
        tvAvatarSizePercent = findViewById(R.id.tvAvatarSizePercent)

        spinnerAvatarStyle = findViewById(R.id.spinnerAvatarStyle)
        spinnerSoundProfile = findViewById(R.id.spinnerSoundProfile)
        spinnerAnimationSpeed = findViewById(R.id.spinnerAnimationSpeed)
        spinnerColorTheme = findViewById(R.id.spinnerColorTheme)

        switchParticleAnimation = findViewById(R.id.switchParticleAnimation)
        switchAudioWaveform = findViewById(R.id.switchAudioWaveform)
        switchShowStatusText = findViewById(R.id.switchShowStatusText)
        switchReduceMotion = findViewById(R.id.switchReduceMotion)

        switchAudioFeedback = findViewById(R.id.switchAudioFeedback)
        switchHapticFeedback = findViewById(R.id.switchHapticFeedback)

        btnPreviewIdle = findViewById(R.id.btnPreviewIdle)
        btnPreviewWorking = findViewById(R.id.btnPreviewWorking)
        btnPreviewSpeaking = findViewById(R.id.btnPreviewSpeaking)
        btnPreviewSuccess = findViewById(R.id.btnPreviewSuccess)

        switchOverlayEnabled = findViewById(R.id.switchOverlayEnabled)
        switchDynamicIsland = findViewById(R.id.switchDynamicIsland)
        radioOverlayMode = findViewById(R.id.radioOverlayMode)
        spinnerOverlayEdge = findViewById(R.id.spinnerOverlayEdge)

        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        btnTopSave = findViewById(R.id.btnTopSave)
        tvSaveFeedback = findViewById(R.id.tvSaveFeedback)
        avatarGallery = findViewById(R.id.avatarGallery)

        // Setup preview state spinner
        val stateNames = stateOptions.map { it.displayName }
        val stateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, stateNames)
        spinnerPreviewState.adapter = stateAdapter
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            val initial = dataStore.loadCurrentSettings()

            val currentScale = initial.avatarScalePercent.coerceIn(50, 150)
            seekBarAvatarSize.progress = currentScale - 50
            tvAvatarSizePercent.text = "$currentScale%"
            previewAvatarView.avatarScaleFactor = currentScale / 100f

            spinnerAvatarStyle.setSelection(styleValues.indexOf(initial.avatarStyle).coerceAtLeast(0))
            spinnerSoundProfile.setSelection(soundProfileValues.indexOf(initial.soundProfile).coerceAtLeast(0))
            spinnerAnimationSpeed.setSelection(speedValues.indexOf(initial.animationSpeed).coerceAtLeast(0))
            spinnerColorTheme.setSelection(themeValues.indexOf(initial.colorTheme).coerceAtLeast(0))

            switchParticleAnimation.isChecked = initial.particleAnimation
            switchAudioWaveform.isChecked = initial.audioWaveform
            switchShowStatusText.isChecked = initial.showStatusText
            switchReduceMotion.isChecked = initial.reduceMotion

            switchAudioFeedback.isChecked = initial.audioFeedback
            switchHapticFeedback.isChecked = initial.hapticFeedback

            switchOverlayEnabled.isChecked = initial.overlayEnabled
            switchDynamicIsland.isChecked = initial.dynamicIslandEnabled

            radioOverlayMode.check(if (preferences.taskOverlayMode == "working") R.id.rbOverlayWorking else R.id.rbOverlayAlways)
            spinnerOverlayEdge.setSelection(edgeValues.indexOf(preferences.taskOverlayEdge).coerceAtLeast(0))

            previewAvatarView.setState(AvatarState.LISTENING)
            spinnerPreviewState.setSelection(stateOptions.indexOf(AvatarState.LISTENING).coerceAtLeast(0))
        }
    }

    private fun setupListeners() {
        spinnerPreviewState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                stateOptions.getOrNull(position)?.let { previewAvatarView.setState(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val previewUpdateListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyLivePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerAvatarStyle.onItemSelectedListener = previewUpdateListener
        spinnerAnimationSpeed.onItemSelectedListener = previewUpdateListener
        spinnerColorTheme.onItemSelectedListener = previewUpdateListener

        spinnerSoundProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val profile = soundProfileValues.getOrNull(position) ?: "Cyber Neon"
                preferences.avatarSoundProfile = profile
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        // Live Audio Audition & Preview buttons
        btnPreviewIdle.setOnClickListener {
            val profile = soundProfileValues.getOrNull(spinnerSoundProfile.selectedItemPosition) ?: "Cyber Neon"
            GrokBotSoundManager.getInstance(this).playPreviewSound("idle", profile)
            previewAvatarView.setState(AvatarState.IDLE)
            spinnerPreviewState.setSelection(stateOptions.indexOf(AvatarState.IDLE).coerceAtLeast(0))
        }

        btnPreviewWorking.setOnClickListener {
            val profile = soundProfileValues.getOrNull(spinnerSoundProfile.selectedItemPosition) ?: "Cyber Neon"
            GrokBotSoundManager.getInstance(this).playPreviewSound("working", profile)
            previewAvatarView.setState(AvatarState.THINKING)
            spinnerPreviewState.setSelection(stateOptions.indexOf(AvatarState.THINKING).coerceAtLeast(0))
        }

        btnPreviewSpeaking.setOnClickListener {
            val profile = soundProfileValues.getOrNull(spinnerSoundProfile.selectedItemPosition) ?: "Cyber Neon"
            GrokBotSoundManager.getInstance(this).playPreviewSound("speaking", profile)
            previewAvatarView.setState(AvatarState.SPEAKING)
            spinnerPreviewState.setSelection(stateOptions.indexOf(AvatarState.SPEAKING).coerceAtLeast(0))
        }

        btnPreviewSuccess.setOnClickListener {
            val profile = soundProfileValues.getOrNull(spinnerSoundProfile.selectedItemPosition) ?: "Cyber Neon"
            GrokBotSoundManager.getInstance(this).playPreviewSound("success", profile)
            previewAvatarView.setState(AvatarState.SUCCESS)
            spinnerPreviewState.setSelection(stateOptions.indexOf(AvatarState.SUCCESS).coerceAtLeast(0))
        }

        seekBarAvatarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val percent = (progress + 50).coerceIn(50, 150)
                tvAvatarSizePercent.text = "$percent%"
                previewAvatarView.avatarScaleFactor = percent / 100f
                preferences.avatarScalePercent = percent
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        switchParticleAnimation.setOnCheckedChangeListener { _, _ -> applyLivePreview() }
        switchAudioWaveform.setOnCheckedChangeListener { _, _ -> applyLivePreview() }
        switchShowStatusText.setOnCheckedChangeListener { _, _ -> applyLivePreview() }
        switchReduceMotion.setOnCheckedChangeListener { _, _ -> applyLivePreview() }

        btnSaveSettings.setOnClickListener { saveAndApplySettings() }
        btnTopSave.setOnClickListener { saveAndApplySettings() }
    }

    private fun applyLivePreview() {
        val selectedStyle = styleValues.getOrNull(spinnerAvatarStyle.selectedItemPosition) ?: "Droid"
        val selectedSpeed = speedValues.getOrNull(spinnerAnimationSpeed.selectedItemPosition) ?: "Normal"
        val selectedTheme = themeValues.getOrNull(spinnerColorTheme.selectedItemPosition) ?: "Blue"

        preferences.avatarStyle = selectedStyle
        preferences.taskOverlayAvatar = selectedStyle
        preferences.avatarAnimationSpeed = selectedSpeed
        preferences.avatarColorTheme = selectedTheme
        preferences.isParticleAnimationEnabled = switchParticleAnimation.isChecked
        preferences.isAudioWaveformEnabled = switchAudioWaveform.isChecked
        preferences.isShowStatusTextEnabled = switchShowStatusText.isChecked
        preferences.isReduceMotionEnabled = switchReduceMotion.isChecked

        selectInGallery(selectedStyle)
        previewAvatarView.setAvatarStyle(selectedStyle)
        previewAvatarView.invalidate()

        // Auto-save asynchronously so selection is immediately usable
        lifecycleScope.launch {
            val currentSettings = dataStore.loadCurrentSettings()
            dataStore.saveSettings(currentSettings.copy(
                avatarStyle = selectedStyle,
                animationSpeed = selectedSpeed,
                colorTheme = selectedTheme,
                particleAnimation = switchParticleAnimation.isChecked,
                audioWaveform = switchAudioWaveform.isChecked,
                showStatusText = switchShowStatusText.isChecked,
                reduceMotion = switchReduceMotion.isChecked
            ))
        }
    }

    private fun saveAndApplySettings() {
        btnSaveSettings.isEnabled = false
        btnTopSave.isEnabled = false

        val selectedStyle = styleValues.getOrNull(spinnerAvatarStyle.selectedItemPosition) ?: "MetaMuse"
        val selectedSoundProfile = soundProfileValues.getOrNull(spinnerSoundProfile.selectedItemPosition) ?: "Cyber Neon"
        val selectedSpeed = speedValues.getOrNull(spinnerAnimationSpeed.selectedItemPosition) ?: "Normal"
        val selectedTheme = themeValues.getOrNull(spinnerColorTheme.selectedItemPosition) ?: "Blue"
        val selectedEdge = edgeValues.getOrNull(spinnerOverlayEdge.selectedItemPosition) ?: "right"
        val isWorkingMode = radioOverlayMode.checkedRadioButtonId == R.id.rbOverlayWorking
        val scalePercent = (seekBarAvatarSize.progress + 50).coerceIn(50, 150)

        val newSettings = AvatarSettings(
            avatarStyle = selectedStyle,
            soundProfile = selectedSoundProfile,
            animationSpeed = selectedSpeed,
            colorTheme = selectedTheme,
            particleAnimation = switchParticleAnimation.isChecked,
            audioWaveform = switchAudioWaveform.isChecked,
            audioFeedback = switchAudioFeedback.isChecked,
            hapticFeedback = switchHapticFeedback.isChecked,
            showStatusText = switchShowStatusText.isChecked,
            reduceMotion = switchReduceMotion.isChecked,
            dynamicIslandEnabled = switchDynamicIsland.isChecked,
            overlayEnabled = switchOverlayEnabled.isChecked,
            avatarScalePercent = scalePercent
        )

        lifecycleScope.launch {
            val result = dataStore.saveSettings(newSettings)
            btnSaveSettings.isEnabled = true
            btnTopSave.isEnabled = true

            if (result.isSuccess) {
                preferences.taskOverlayMode = if (isWorkingMode) "working" else "always"
                preferences.taskOverlayEdge = selectedEdge

                tvSaveFeedback.visibility = View.VISIBLE
                tvSaveFeedback.text = "✓ Settings saved and applied successfully!"
                tvSaveFeedback.setTextColor(android.graphics.Color.parseColor("#00E676"))

                Snackbar.make(findViewById(R.id.overlayAvatarRoot), "Overlay Avatar settings saved successfully!", Snackbar.LENGTH_SHORT).show()
            } else {
                tvSaveFeedback.visibility = View.VISIBLE
                tvSaveFeedback.text = "⚠ Failed to save settings: ${result.exceptionOrNull()?.message}"
                tvSaveFeedback.setTextColor(android.graphics.Color.parseColor("#FF1744"))
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupGallery() {
        avatarGallery.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
        }
        avatarGallery.setBackgroundColor(0)
        val bridge = AvatarPickerBridge()
        avatarGallery.addJavascriptInterface(bridge, "JarvisAvatarPicker")
        avatarGallery.addJavascriptInterface(bridge, "AndroidBridge")
        avatarGallery.webViewClient = object : WebViewClientCompat() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val activeAvatar = preferences.taskOverlayAvatar.ifBlank { preferences.avatarStyle }
                selectInGallery(activeAvatar)
                avatarGallery.evaluateJavascript(
                    "window.setGalleryTheme && window.setGalleryTheme('#${themeColor(R.attr.themeTextPrimary)}', '#${themeColor(R.attr.themeAccent)}')",
                    null
                )
            }
        }

        try {
            val htmlContent = assets.open("overlay-toys/gallery.html").bufferedReader().use { it.readText() }
            avatarGallery.loadDataWithBaseURL("file:///android_asset/overlay-toys/", htmlContent, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            avatarGallery.loadUrl("file:///android_asset/overlay-toys/gallery.html")
        }
    }

    private fun selectInGallery(name: String) {
        val safeName = name.trim().lowercase()
        avatarGallery.evaluateJavascript("window.setSelectedAvatar && window.setSelectedAvatar('$safeName')", null)
    }

    private fun themeColor(attribute: Int): String {
        val value = android.util.TypedValue()
        theme.resolveAttribute(attribute, value, true)
        return String.format(java.util.Locale.US, "%06X", 0xFFFFFF and value.data)
    }

    private inner class AvatarPickerBridge {
        @JavascriptInterface
        fun pageReady() {
            runOnUiThread {
                val activeAvatar = preferences.taskOverlayAvatar.ifBlank { preferences.avatarStyle }
                selectInGallery(activeAvatar)
            }
        }

        @JavascriptInterface
        fun selectAvatar(name: String) {
            runOnUiThread {
                val cleanName = name.trim()
                // Instantly update preferences
                preferences.taskOverlayAvatar = cleanName
                preferences.avatarStyle = cleanName
                selectInGallery(cleanName)

                // Match with spinner if available
                val matchedStyleIdx = styleValues.indexOfFirst { it.equals(cleanName, ignoreCase = true) }
                if (matchedStyleIdx >= 0) {
                    spinnerAvatarStyle.setSelection(matchedStyleIdx)
                }

                // Immediately update live preview avatar view
                previewAvatarView.setAvatarStyle(cleanName)
                previewAvatarView.invalidate()

                // Immediately persist settings asynchronously so user does NOT have to press save button
                lifecycleScope.launch {
                    val currentSettings = dataStore.loadCurrentSettings()
                    dataStore.saveSettings(currentSettings.copy(avatarStyle = cleanName))
                }

                Snackbar.make(
                    findViewById(R.id.overlayAvatarRoot),
                    "✓ Avatar selected: ${cleanName.replaceFirstChar { it.uppercase() }}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        if (::avatarGallery.isInitialized) {
            avatarGallery.stopLoading()
            avatarGallery.destroy()
        }
        super.onDestroy()
    }
}
