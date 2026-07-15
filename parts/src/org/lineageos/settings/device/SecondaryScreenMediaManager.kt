package org.lineageos.settings.device

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

@SuppressLint("ViewConstructor")
class OverlayComposeWrapper(context: Context) : FrameLayout(context), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    val composeView = ComposeView(context)

    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    init {
        setViewTreeLifecycleOwner(this)
        setViewTreeViewModelStoreOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        addView(composeView)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

class SecondaryScreenMediaManager(private val mContext: Context) : DisplayManager.DisplayListener {

    private val mDisplayManager = mContext.getSystemService(DisplayManager::class.java)!!
    private val mMediaSessionManager = mContext.getSystemService(MediaSessionManager::class.java)!!
    private val mAudioManager = mContext.getSystemService(AudioManager::class.java)!!
    private val mHandler = Handler(Looper.getMainLooper())

    private var mWindowManager: WindowManager? = null
    private var mMediaView: OverlayComposeWrapper? = null
    private var mActiveMediaController: MediaController? = null

    private var isMainScreenImmersive = false
    private var mainTrackerView: android.view.View? = null
    private var mainWindowManager: WindowManager? = null
    private var secondaryDisplay: Display? = null
    private var wasShowing = false

    private val visibilityHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { removeMediaControls() }
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (mMediaView != null) {
                evaluateVisibility()
                visibilityHandler.postDelayed(this, 1000)
            }
        }
    }

    private var mediaTitle by mutableStateOf("No Media Playing")
    private var mediaAppName by mutableStateOf("Unknown App")
    private var isPlaying by mutableStateOf(false)
    private var volume by mutableIntStateOf(0)
    private var maxVolume by mutableIntStateOf(100)
    private var brightness by mutableIntStateOf(128)
    private var mediaArt by mutableStateOf<Bitmap?>(null)

    private val mSessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateActiveController(controllers)
    }

    private val mControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
            evaluateVisibility()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(metadata)
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                val type = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                if (type == AudioManager.STREAM_MUSIC) {
                    volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }
        }
    }

    fun start() {
        setupMainScreenTracker()
        mDisplayManager.registerDisplayListener(this, mHandler)
        checkDisplays()
        
        mContext.registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        
        try {
            mMediaSessionManager.addOnActiveSessionsChangedListener(mSessionListener, null, mHandler)
            updateActiveController(mMediaSessionManager.getActiveSessions(null))
        } catch (e: SecurityException) {
            Log.e("SecondaryScreenMediaManager", "Missing MEDIA_CONTENT_CONTROL permission", e)
        }
        maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            brightness = Settings.System.getInt(mContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            Log.e("SecondaryScreenMediaManager", "Brightness setting not found", e)
        }
    }

    fun shutdown() {
        try { mContext.unregisterReceiver(volumeReceiver) } catch (e: Exception) {}
        mDisplayManager.unregisterDisplayListener(this)
        mMediaSessionManager.removeOnActiveSessionsChangedListener(mSessionListener)
        visibilityHandler.removeCallbacksAndMessages(null)
        removeMediaControls()
        mainTrackerView?.let { mainWindowManager?.removeView(it) }
    }

    private fun setupMainScreenTracker() {
        val mainDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val displayContext = mContext.createDisplayContext(mainDisplay)
        mainWindowManager = displayContext.getSystemService(WindowManager::class.java)
        mainTrackerView = android.view.View(displayContext).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnApplyWindowInsetsListener { _, insets ->
                val statusVisible = insets.isVisible(android.view.WindowInsets.Type.statusBars())
                isMainScreenImmersive = !statusVisible
                evaluateVisibility()
                insets
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        try {
            mainWindowManager?.addView(mainTrackerView, params)
        } catch (e: Exception) {
            Log.e("SecondaryScreenMediaManager", "Failed to add main tracker view", e)
        }
    }

    private fun getForegroundPackage(): String? {
        val am = mContext.getSystemService(android.app.ActivityManager::class.java)
        return try {
            val tasks = am.getRunningTasks(1)
            if (tasks.isNotEmpty()) tasks[0].topActivity?.packageName else null
        } catch (e: Exception) { null }
    }

    private fun evaluateVisibility() {
        val fgPkg = getForegroundPackage()
        val activePkg = mActiveMediaController?.packageName
        val isForeground = activePkg != null && activePkg == fgPkg
        val isPlayingState = mActiveMediaController?.playbackState?.state == PlaybackState.STATE_PLAYING || 
                             mActiveMediaController?.playbackState?.state == PlaybackState.STATE_BUFFERING
        val shouldShow = isForeground && isMainScreenImmersive && (isPlayingState || wasShowing)
        if (shouldShow) {
            visibilityHandler.removeCallbacks(hideRunnable)
            wasShowing = true
            if (mMediaView == null) {
                showMediaControlsInternal()
                visibilityHandler.postDelayed(pollRunnable, 1000)
            }
        } else {
            wasShowing = false
            if (mMediaView != null) {
                visibilityHandler.removeCallbacks(hideRunnable)
                visibilityHandler.postDelayed(hideRunnable, 2000)
            }
        }
    }

    private fun checkDisplays() {
        val displays = mDisplayManager.displays
        for (display in displays) {
            if (display.displayId != Display.DEFAULT_DISPLAY) {
                secondaryDisplay = display
                evaluateVisibility()
                break
            }
        }
    }

    override fun onDisplayAdded(displayId: Int) {
        val display = mDisplayManager.getDisplay(displayId)
        if (display != null && display.displayId != Display.DEFAULT_DISPLAY) {
            secondaryDisplay = display
            evaluateVisibility()
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        if (secondaryDisplay?.displayId == displayId) {
            secondaryDisplay = null
            removeMediaControls()
        }
    }

    override fun onDisplayChanged(displayId: Int) {}

    private fun showMediaControlsInternal() {
        val display = secondaryDisplay ?: return
        if (mMediaView != null) return
        val displayContext = mContext.createDisplayContext(display)
        mWindowManager = displayContext.getSystemService(WindowManager::class.java)
        val displayMetrics = displayContext.resources.displayMetrics
        val customDensity = displayMetrics.widthPixels / 412f
        mMediaView = OverlayComposeWrapper(displayContext).apply {
            composeView.setContent {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = customDensity, fontScale = 1f)
                ) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        MediaControlsScreen(
                            title = mediaTitle, appName = mediaAppName, isPlaying = isPlaying,
                            volume = volume, maxVolume = maxVolume, brightness = brightness, art = mediaArt,
                            onPlayPause = {
                                val state = mActiveMediaController?.playbackState?.state
                                if (state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING) {
                                    mActiveMediaController?.transportControls?.pause()
                                } else {
                                    mActiveMediaController?.transportControls?.play()
                                }
                            },
                            onPrev = { mActiveMediaController?.transportControls?.skipToPrevious() },
                            onNext = { mActiveMediaController?.transportControls?.skipToNext() },
                            onRewind = { mActiveMediaController?.transportControls?.rewind() },
                            onFastForward = { mActiveMediaController?.transportControls?.fastForward() },
                            onVolumeChange = { v -> volume = v; mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) },
                            onBrightnessChange = { b -> brightness = b; Settings.System.putInt(mContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, b) },
                            onClose = { removeMediaControls() }
                        )
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        mWindowManager?.addView(mMediaView, params)
        mActiveMediaController?.let { updateMetadata(it.metadata); updatePlaybackState(it.playbackState) }
    }

    private fun removeMediaControls() {
        mMediaView?.let { mWindowManager?.removeView(it); it.destroy() }
        mMediaView = null
    }

    private fun updateActiveController(controllers: List<MediaController>?) {
        mActiveMediaController?.unregisterCallback(mControllerCallback)
        if (!controllers.isNullOrEmpty()) {
            mActiveMediaController = controllers[0].also {
                it.registerCallback(mControllerCallback)
                updateMetadata(it.metadata)
                updatePlaybackState(it.playbackState)
            }
            try {
                val pm = mContext.packageManager
                val pkgName = mActiveMediaController!!.packageName
                val name = pm.getApplicationLabel(pm.getApplicationInfo(pkgName, 0))
                mediaAppName = name.toString()
            } catch (e: PackageManager.NameNotFoundException) { mediaAppName = mActiveMediaController!!.packageName }
        } else {
            mActiveMediaController = null
            mediaTitle = "No Media Playing"; mediaAppName = "Unknown App"; mediaArt = null; isPlaying = false
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        if (metadata == null) return
        mediaTitle = metadata.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString() ?: "Unknown Title"
        mediaArt = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
    }
}

@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    // We use a tween to accurately divide the animation into X, 1.2X, X segments
    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "MorphProgress"
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(114.dp)
    ) {
        Canvas(modifier = Modifier.size(56.dp)) {
            val w = size.width
            val h = size.height

            // Map linear progress to accommodate the delay in the middle.
            // Squeeze: 0 to 0.3125 (takes X time)
            // Hold: 0.3125 to 0.6875 (takes 1.2X time)
            // Split: 0.6875 to 1.0 (takes X time)
            val mappedProgress = when {
                progress < 0.3125f -> (progress / 0.3125f) * 0.5f
                progress > 0.6875f -> 0.5f + ((progress - 0.6875f) / 0.3125f) * 0.5f
                else -> 0.5f
            }

            // Helper to interpolate between three states (t=0, t=0.5, t=1) using our custom mapped progress
            fun lerp(start: Float, mid: Float, end: Float, t: Float): Float {
                return if (t <= 0.5f) {
                    val p = t * 2f
                    start + (mid - start) * p
                } else {
                    val p = (t - 0.5f) * 2f
                    mid + (end - mid) * p
                }
            }

            // Path 1 (Top half of triangle -> Left bar)
            val p1 = Path().apply {
                // Point 1 (Top Left)
                val x1 = lerp(0.067f, 0.45f, 0.1f, mappedProgress) * w
                val y1 = lerp(0f, 0f, 0f, mappedProgress) * h
                moveTo(x1, y1)

                // Point 2 (Right tip -> Right edge of left bar)
                val x2 = lerp(0.933f, 0.55f, 0.4f, mappedProgress) * w
                val y2 = lerp(0.5f, 0f, 0f, mappedProgress) * h // Squeezes to top for line
                lineTo(x2, y2)

                // Point 3 (Right tip -> Bottom right edge of left bar)
                val x3 = lerp(0.933f, 0.55f, 0.4f, mappedProgress) * w
                val y3 = lerp(0.505f, 1f, 1f, mappedProgress) * h
                lineTo(x3, y3)

                // Point 4 (Center Left -> Bottom left edge of left bar)
                val x4 = lerp(0.067f, 0.45f, 0.1f, mappedProgress) * w
                val y4 = lerp(0.505f, 1f, 1f, mappedProgress) * h
                lineTo(x4, y4)
                
                close()
            }

            // Path 2 (Bottom half of triangle -> Right bar)
            val p2 = Path().apply {
                // Point 1 (Center Left -> Top left edge of right bar)
                val x1 = lerp(0.067f, 0.45f, 0.6f, mappedProgress) * w
                val y1 = lerp(0.495f, 0f, 0f, mappedProgress) * h
                moveTo(x1, y1)

                // Point 2 (Right tip -> Top right edge of right bar)
                val x2 = lerp(0.933f, 0.55f, 0.9f, mappedProgress) * w
                val y2 = lerp(0.495f, 0f, 0f, mappedProgress) * h
                lineTo(x2, y2)

                // Point 3 (Right tip -> Bottom right edge of right bar)
                val x3 = lerp(0.933f, 0.55f, 0.9f, mappedProgress) * w
                val y3 = lerp(0.5f, 1f, 1f, mappedProgress) * h
                lineTo(x3, y3)

                // Point 4 (Bottom Left -> Bottom left edge of right bar)
                val x4 = lerp(0.067f, 0.45f, 0.6f, mappedProgress) * w
                val y4 = lerp(1f, 1f, 1f, mappedProgress) * h
                lineTo(x4, y4)
                
                close()
            }

            val combinedPath = Path().apply {
                op(p1, p2, androidx.compose.ui.graphics.PathOperation.Union)
            }

            drawIntoCanvas { canvas ->
                val paint = androidx.compose.ui.graphics.Paint().apply {
                    color = Color.White
                    isAntiAlias = true
                    pathEffect = androidx.compose.ui.graphics.PathEffect.cornerPathEffect(6.dp.toPx())
                }
                canvas.drawPath(combinedPath, paint)
            }
        }
    }
}

@Composable
fun MediaControlsScreen(
    title: String, appName: String, isPlaying: Boolean, volume: Int, maxVolume: Int, brightness: Int, art: Bitmap?,
    onPlayPause: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onRewind: () -> Unit, onFastForward: () -> Unit,
    onVolumeChange: (Int) -> Unit, onBrightnessChange: (Int) -> Unit, onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (art != null) {
                Image(
                    bitmap = art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(30.dp)
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }

            Column(
                modifier = Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                // Header Row (Thumbnail + Text)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.1f)))
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(text = appName, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                // Primary Controls
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(84.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", modifier = Modifier.size(62.dp), tint = Color.White)
                    }
                    
                    MorphingPlayPauseButton(isPlaying = isPlaying, onClick = onPlayPause)
                    
                    IconButton(onClick = onNext, modifier = Modifier.size(84.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(62.dp), tint = Color.White)
                    }
                }
                
                // Secondary Controls
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onRewind, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Rounded.FastRewind, contentDescription = "Rewind", modifier = Modifier.size(52.dp), tint = Color.White)
                    }
                    IconButton(onClick = onFastForward, modifier = Modifier.size(72.dp)) {
                        Icon(Icons.Rounded.FastForward, contentDescription = "Fast Forward", modifier = Modifier.size(52.dp), tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                SliderRow(label = "Vol", value = volume.toFloat(), range = 0f..maxVolume.toFloat(), onValueChange = onVolumeChange)
                SliderRow(label = "Bright", value = brightness.toFloat(), range = 0f..255f, onValueChange = onBrightnessChange)
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Slider(
            value = value, onValueChange = { onValueChange(it.toInt()) }, valueRange = range, modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
        )
    }
}
