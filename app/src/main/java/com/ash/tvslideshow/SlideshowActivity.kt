package com.ash.tvslideshow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Displays images and videos in a slideshow with smooth transitions.
 * - UP: Show/hide controls overlay
 * - LEFT/RIGHT: Previous/next media
 * - CENTER/ENTER: Pause/Resume
 * - BACK: Return to settings
 */
class SlideshowActivity : ComponentActivity() {

    private lateinit var imageView1: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var playerView: PlayerView
    private lateinit var controlsOverlay: LinearLayout
    private lateinit var mediaCountText: TextView
    private lateinit var delayText: TextView
    private lateinit var statusText: TextView

    private var exoPlayer: ExoPlayer? = null
    private var mediaFiles: List<File> = emptyList()
    private var displayOrder: MutableList<Int> = mutableListOf()
    private var currentOrderIndex = 0
    private var isPlaying = true
    private var delayMillis = 5000L
    private var useFirstImageView = true
    private var orderMode = SlideshowPrefs.ORDER_RANDOM
    private var isTransitioning = false
    private var currentBitmap1: Bitmap? = null
    private var currentBitmap2: Bitmap? = null
    private var muteVideos = false
    private var isShowingVideo = false

    private val fadeDuration = 800L
    private val inputDebounceMs = 300L
    private var lastInputTime = 0L

    private val handler = Handler(Looper.getMainLooper())

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                // Video finished, advance to next media
                if (isPlaying && !isFinishing) {
                    handler.post {
                        val nextIndex = getNextOrderIndex()
                        showMedia(nextIndex)
                    }
                }
            }
        }
    }

    private fun scheduleNextImage() {
        if (isPlaying && mediaFiles.isNotEmpty() && displayOrder.isNotEmpty() && !isFinishing && !isShowingVideo) {
            handler.postDelayed({
                if (isPlaying && mediaFiles.isNotEmpty() && displayOrder.isNotEmpty() && !isFinishing) {
                    val nextIndex = getNextOrderIndex()
                    showMedia(nextIndex)
                }
            }, delayMillis)
        }
    }

    private fun canProcessInput(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastInputTime < inputDebounceMs) {
            return false
        }
        lastInputTime = now
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_slideshow)

        imageView1 = findViewById(R.id.slideshow_image1)
        imageView2 = findViewById(R.id.slideshow_image2)
        playerView = findViewById(R.id.video_player_view)
        controlsOverlay = findViewById(R.id.controls_overlay)
        mediaCountText = findViewById(R.id.image_count_text)
        delayText = findViewById(R.id.delay_text)
        statusText = findViewById(R.id.slideshow_status_text)

        imageView1.alpha = 1f
        imageView2.alpha = 0f

        initializePlayer()
        loadSettings()
        loadMedia()

        if (mediaFiles.isNotEmpty()) {
            showFirstMedia()
            startSlideshow()
        } else {
            statusText.text = "No media found"
            statusText.visibility = View.VISIBLE
        }
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(playerListener)
        }
        playerView.player = exoPlayer
    }

    private fun loadSettings() {
        val seconds = SlideshowPrefs.getDelaySeconds(this)
        delayMillis = seconds * 1000L
        muteVideos = SlideshowPrefs.getMuteVideos(this)
        exoPlayer?.volume = if (muteVideos) 0f else 1f
    }

    private fun loadMedia() {
        val folderPath = SlideshowPrefs.getFolderPath(this)
        val includeSubfolders = SlideshowPrefs.getIncludeSubfolders(this)
        orderMode = SlideshowPrefs.getDisplayOrder(this)

        var files = MediaLoader.getMediaFiles(folderPath, includeSubfolders)

        mediaFiles = when (orderMode) {
            SlideshowPrefs.ORDER_DATE_ASC -> files.sortedBy { it.lastModified() }
            SlideshowPrefs.ORDER_DATE_DESC -> files.sortedByDescending { it.lastModified() }
            else -> files
        }

        initializeDisplayOrder()
    }

    private fun initializeDisplayOrder() {
        displayOrder = (0 until mediaFiles.size).toMutableList()

        if (orderMode == SlideshowPrefs.ORDER_RANDOM) {
            displayOrder.shuffle()
        }

        currentOrderIndex = 0
    }

    private fun getNextOrderIndex(): Int {
        currentOrderIndex++

        if (currentOrderIndex >= displayOrder.size) {
            if (orderMode == SlideshowPrefs.ORDER_RANDOM) {
                displayOrder.shuffle()
            }
            currentOrderIndex = 0
        }

        return displayOrder[currentOrderIndex]
    }

    private fun getPreviousOrderIndex(): Int {
        currentOrderIndex--

        if (currentOrderIndex < 0) {
            currentOrderIndex = displayOrder.size - 1
        }

        return displayOrder[currentOrderIndex]
    }

    private fun loadBitmap(file: File): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > 1920 || options.outHeight / sampleSize > 1080) {
                sampleSize *= 2
            }

            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, finalOptions)
                ?: return null

            val rotation = getExifRotation(file)
            if (rotation != 0) {
                rotateBitmap(bitmap, rotation)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getExifRotation(file: File): Int {
        return try {
            val exif = ExifInterface(file.absolutePath)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees.toFloat())
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private fun showFirstMedia() {
        if (mediaFiles.isEmpty() || displayOrder.isEmpty()) return

        currentOrderIndex = 0
        val mediaIndex = displayOrder[currentOrderIndex]

        if (mediaIndex < 0 || mediaIndex >= mediaFiles.size) return

        val file = mediaFiles[mediaIndex]

        if (!file.exists()) {
            handleMissingFile(mediaIndex)
            return
        }

        if (MediaLoader.isVideo(file)) {
            showVideo(file)
        } else {
            showFirstImage(file)
        }

        updateOverlayInfo()
    }

    private fun showFirstImage(file: File) {
        val bitmap = loadBitmap(file) ?: return

        stopVideo()

        currentBitmap1?.recycle()
        currentBitmap1 = bitmap

        imageView1.setImageBitmap(bitmap)
        imageView1.alpha = 1f
        imageView2.alpha = 0f
        imageView1.visibility = View.VISIBLE
        imageView2.visibility = View.VISIBLE
        useFirstImageView = true
        isShowingVideo = false
    }

    private fun showVideo(file: File) {
        isShowingVideo = true
        isTransitioning = true

        // Hide image views
        imageView1.animate().alpha(0f).setDuration(fadeDuration / 2).start()
        imageView2.animate().alpha(0f).setDuration(fadeDuration / 2).start()

        // Show and start video
        playerView.visibility = View.VISIBLE
        playerView.alpha = 0f
        playerView.animate().alpha(1f).setDuration(fadeDuration / 2).start()

        exoPlayer?.let { player ->
            player.volume = if (muteVideos) 0f else 1f
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = isPlaying
        }

        handler.postDelayed({
            isTransitioning = false
        }, fadeDuration)
    }

    private fun stopVideo() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        playerView.visibility = View.GONE
        isShowingVideo = false
    }

    private fun handleMissingFile(index: Int) {
        if (displayOrder.size > 1) {
            displayOrder.remove(index)
            if (currentOrderIndex >= displayOrder.size) {
                currentOrderIndex = 0
            }
            if (displayOrder.isNotEmpty()) {
                showMedia(displayOrder[currentOrderIndex])
            }
        } else {
            statusText.text = "No media available"
            statusText.visibility = View.VISIBLE
            stopSlideshow()
        }
    }

    private fun showMedia(index: Int) {
        if (mediaFiles.isEmpty() || displayOrder.isEmpty() || isFinishing) return

        val mediaIndex = index.coerceIn(0, mediaFiles.lastIndex)
        val file = mediaFiles[mediaIndex]

        if (!file.exists()) {
            handleMissingFile(mediaIndex)
            return
        }

        if (MediaLoader.isVideo(file)) {
            showVideo(file)
        } else {
            showImageWithCrossfade(file)
        }

        updateOverlayInfo()
    }

    private fun showImageWithCrossfade(file: File) {
        val bitmap = loadBitmap(file)

        if (bitmap == null) {
            if (isPlaying) {
                scheduleNextImage()
            }
            return
        }

        // Stop any video that might be playing
        if (isShowingVideo) {
            stopVideo()
            imageView1.visibility = View.VISIBLE
            imageView2.visibility = View.VISIBLE
        }

        isTransitioning = true
        isShowingVideo = false

        val showingView: ImageView
        val hiddenView: ImageView

        if (useFirstImageView) {
            showingView = imageView1
            hiddenView = imageView2
            currentBitmap2?.recycle()
            currentBitmap2 = bitmap
        } else {
            showingView = imageView2
            hiddenView = imageView1
            currentBitmap1?.recycle()
            currentBitmap1 = bitmap
        }

        hiddenView.setImageBitmap(bitmap)

        showingView.animate().cancel()
        hiddenView.animate().cancel()

        hiddenView.alpha = 0f
        hiddenView.animate()
            .alpha(1f)
            .setDuration(fadeDuration)
            .withEndAction {
                isTransitioning = false
                if (!isFinishing) {
                    scheduleNextImage()
                }
            }
            .start()

        showingView.animate()
            .alpha(0f)
            .setDuration(fadeDuration)
            .start()

        useFirstImageView = !useFirstImageView
    }

    private fun showNextMedia() {
        if (mediaFiles.isEmpty()) return
        handler.removeCallbacksAndMessages(null)
        val nextIndex = getNextOrderIndex()
        showMedia(nextIndex)
    }

    private fun showPreviousMedia() {
        if (mediaFiles.isEmpty()) return
        handler.removeCallbacksAndMessages(null)
        val prevIndex = getPreviousOrderIndex()
        showMedia(prevIndex)
    }

    private fun startSlideshow() {
        isPlaying = true
        loadSettings()
        handler.removeCallbacksAndMessages(null)

        if (isShowingVideo) {
            exoPlayer?.playWhenReady = true
        } else {
            scheduleNextImage()
        }

        updateOverlayInfo()
    }

    private fun stopSlideshow() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)

        if (isShowingVideo) {
            exoPlayer?.playWhenReady = false
        }

        updateOverlayInfo()
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun toggleControlsOverlay() {
        controlsOverlay.visibility = if (controlsOverlay.visibility == View.VISIBLE) {
            View.GONE
        } else {
            updateOverlayInfo()
            View.VISIBLE
        }
    }

    private fun updateOverlayInfo() {
        val displayPosition = currentOrderIndex + 1
        val currentFile = if (displayOrder.isNotEmpty() && currentOrderIndex < displayOrder.size) {
            val idx = displayOrder[currentOrderIndex]
            if (idx < mediaFiles.size) mediaFiles[idx] else null
        } else null

        val mediaType = if (currentFile != null && MediaLoader.isVideo(currentFile)) "Video" else "Image"
        mediaCountText.text = "$mediaType $displayPosition of ${mediaFiles.size}"

        if (isShowingVideo) {
            delayText.text = "Playing video"
        } else {
            delayText.text = "Delay: ${delayMillis / 1000}s"
        }

        statusText.text = if (isPlaying) "Playing" else "Paused"
        statusText.visibility = View.VISIBLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                toggleControlsOverlay()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (canProcessInput() && !isTransitioning) {
                    showPreviousMedia()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (canProcessInput() && !isTransitioning) {
                    showNextMedia()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (canProcessInput()) {
                    togglePlayPause()
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.playWhenReady = false
    }

    override fun onResume() {
        super.onResume()
        loadSettings()

        val folderPath = SlideshowPrefs.getFolderPath(this)
        val folder = File(folderPath)
        if (!folder.exists() || !folder.canRead()) {
            statusText.text = "Folder not accessible"
            statusText.visibility = View.VISIBLE
            stopSlideshow()
            return
        }

        if (isPlaying && mediaFiles.isNotEmpty() && displayOrder.isNotEmpty()) {
            if (isShowingVideo) {
                exoPlayer?.playWhenReady = true
            } else {
                scheduleNextImage()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)

        imageView1.animate().cancel()
        imageView2.animate().cancel()

        imageView1.setImageBitmap(null)
        imageView2.setImageBitmap(null)

        currentBitmap1?.recycle()
        currentBitmap1 = null
        currentBitmap2?.recycle()
        currentBitmap2 = null

        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null

        mediaFiles = emptyList()
        displayOrder.clear()
    }
}
