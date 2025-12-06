package com.ash.tvslideshow

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
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
import java.io.File

/**
 * Displays images in a slideshow with fade transitions.
 * - UP: Show/hide controls overlay
 * - LEFT/RIGHT: Previous/next image
 * - CENTER/ENTER: Pause/Resume
 * - BACK: Return to settings
 */
class SlideshowActivity : ComponentActivity() {

    private lateinit var imageView1: ImageView
    private lateinit var imageView2: ImageView
    private lateinit var controlsOverlay: LinearLayout
    private lateinit var imageCountText: TextView
    private lateinit var delayText: TextView
    private lateinit var statusText: TextView

    private var imageFiles: List<File> = emptyList()
    private var displayOrder: MutableList<Int> = mutableListOf()  // Indices into imageFiles
    private var currentOrderIndex = 0  // Position in displayOrder
    private var isPlaying = true
    private var delayMillis = 5000L
    private var useFirstImageView = true  // Track which ImageView is currently showing
    private var orderMode = SlideshowPrefs.ORDER_RANDOM
    private var isTransitioning = false  // Prevent rapid input during transitions
    private var currentBitmap1: Bitmap? = null  // Track bitmaps for proper recycling
    private var currentBitmap2: Bitmap? = null

    private val fadeDuration = 800L  // milliseconds
    private val inputDebounceMs = 300L  // Minimum time between user inputs
    private var lastInputTime = 0L

    private val handler = Handler(Looper.getMainLooper())

    private fun scheduleNextImage() {
        if (isPlaying && imageFiles.isNotEmpty() && displayOrder.isNotEmpty() && !isFinishing) {
            handler.postDelayed({
                if (isPlaying && imageFiles.isNotEmpty() && displayOrder.isNotEmpty() && !isFinishing) {
                    val nextIndex = getNextOrderIndex()
                    showImageWithCrossfade(nextIndex)
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

        // Keep screen on during slideshow - prevents sleep and screensaver
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_slideshow)

        imageView1 = findViewById(R.id.slideshow_image1)
        imageView2 = findViewById(R.id.slideshow_image2)
        controlsOverlay = findViewById(R.id.controls_overlay)
        imageCountText = findViewById(R.id.image_count_text)
        delayText = findViewById(R.id.delay_text)
        statusText = findViewById(R.id.slideshow_status_text)

        // Initialize both image views
        imageView1.alpha = 1f
        imageView2.alpha = 0f

        loadSettings()
        loadImages()

        if (imageFiles.isNotEmpty()) {
            // Show first image immediately (no fade)
            showFirstImage()
            startSlideshow()
        } else {
            statusText.text = "No images found"
            statusText.visibility = View.VISIBLE
        }
    }

    private fun loadSettings() {
        val seconds = SlideshowPrefs.getDelaySeconds(this)
        delayMillis = seconds * 1000L
    }

    private fun loadImages() {
        val folderPath = SlideshowPrefs.getFolderPath(this)
        val includeSubfolders = SlideshowPrefs.getIncludeSubfolders(this)
        orderMode = SlideshowPrefs.getDisplayOrder(this)

        // Get files and sort by date if needed
        var files = ImageLoader.getImageFiles(folderPath, includeSubfolders)

        imageFiles = when (orderMode) {
            SlideshowPrefs.ORDER_DATE_ASC -> files.sortedBy { it.lastModified() }
            SlideshowPrefs.ORDER_DATE_DESC -> files.sortedByDescending { it.lastModified() }
            else -> files  // Random will be handled by displayOrder
        }

        // Initialize display order
        initializeDisplayOrder()
    }

    private fun initializeDisplayOrder() {
        displayOrder = (0 until imageFiles.size).toMutableList()

        if (orderMode == SlideshowPrefs.ORDER_RANDOM) {
            displayOrder.shuffle()
        }

        currentOrderIndex = 0
    }

    private fun getNextOrderIndex(): Int {
        currentOrderIndex++

        // If we've shown all images, reshuffle for random or loop for date order
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

    private fun getCurrentImageIndex(): Int {
        return if (displayOrder.isNotEmpty()) displayOrder[currentOrderIndex] else 0
    }

    private fun loadBitmap(file: File): Bitmap? {
        return try {
            // Load with inSampleSize for large images to prevent OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // Calculate sample size for images larger than 1920x1080
            var sampleSize = 1
            while (options.outWidth / sampleSize > 1920 || options.outHeight / sampleSize > 1080) {
                sampleSize *= 2
            }

            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, finalOptions)
                ?: return null

            // Check EXIF orientation and rotate if needed
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

    private fun showFirstImage() {
        if (imageFiles.isEmpty() || displayOrder.isEmpty()) return

        currentOrderIndex = 0
        val imageIndex = displayOrder[currentOrderIndex]

        // Validate index is within bounds
        if (imageIndex < 0 || imageIndex >= imageFiles.size) return

        val file = imageFiles[imageIndex]

        // Check file still exists
        if (!file.exists()) {
            handleMissingFile(imageIndex)
            return
        }

        val bitmap = loadBitmap(file)

        if (bitmap != null) {
            // Recycle old bitmap
            currentBitmap1?.recycle()
            currentBitmap1 = bitmap

            imageView1.setImageBitmap(bitmap)
            imageView1.alpha = 1f
            imageView2.alpha = 0f
            useFirstImageView = true
        }

        updateOverlayInfo()
    }

    private fun handleMissingFile(index: Int) {
        // Remove missing file from list and try next
        if (displayOrder.size > 1) {
            displayOrder.remove(index)
            if (currentOrderIndex >= displayOrder.size) {
                currentOrderIndex = 0
            }
            // Try to show next valid image
            if (displayOrder.isNotEmpty()) {
                showImageWithCrossfade(displayOrder[currentOrderIndex])
            }
        } else {
            // No more images
            statusText.text = "No images available"
            statusText.visibility = View.VISIBLE
            stopSlideshow()
        }
    }

    private fun showImageWithCrossfade(index: Int) {
        if (imageFiles.isEmpty() || displayOrder.isEmpty() || isFinishing) return

        val imageIndex = index.coerceIn(0, imageFiles.lastIndex)
        val file = imageFiles[imageIndex]

        // Check file still exists (USB could be disconnected)
        if (!file.exists()) {
            handleMissingFile(imageIndex)
            return
        }

        val bitmap = loadBitmap(file)

        if (bitmap == null) {
            // Failed to load - skip to next image
            if (isPlaying) {
                scheduleNextImage()
            }
            return
        }

        isTransitioning = true

        // Determine which views to use
        val showingView: ImageView
        val hiddenView: ImageView

        if (useFirstImageView) {
            showingView = imageView1
            hiddenView = imageView2
            // Recycle the old bitmap that was in imageView2
            currentBitmap2?.recycle()
            currentBitmap2 = bitmap
        } else {
            showingView = imageView2
            hiddenView = imageView1
            // Recycle the old bitmap that was in imageView1
            currentBitmap1?.recycle()
            currentBitmap1 = bitmap
        }

        // Set the new image on the hidden view
        hiddenView.setImageBitmap(bitmap)

        // Cancel any ongoing animations
        showingView.animate().cancel()
        hiddenView.animate().cancel()

        // Crossfade: fade in hidden view, fade out showing view
        hiddenView.alpha = 0f
        hiddenView.animate()
            .alpha(1f)
            .setDuration(fadeDuration)
            .withEndAction {
                isTransitioning = false
                // Only schedule if activity is still valid
                if (!isFinishing) {
                    scheduleNextImage()
                }
            }
            .start()

        showingView.animate()
            .alpha(0f)
            .setDuration(fadeDuration)
            .start()

        // Swap which view is "active"
        useFirstImageView = !useFirstImageView

        updateOverlayInfo()
    }

    private fun showNextImage() {
        if (imageFiles.isEmpty()) return
        val nextIndex = getNextOrderIndex()
        showImageWithCrossfade(nextIndex)
    }

    private fun showPreviousImage() {
        if (imageFiles.isEmpty()) return
        val prevIndex = getPreviousOrderIndex()
        showImageWithCrossfade(prevIndex)
    }

    private fun startSlideshow() {
        isPlaying = true
        loadSettings()  // Reload in case settings changed
        handler.removeCallbacksAndMessages(null)  // Clear all pending callbacks
        scheduleNextImage()
        updateOverlayInfo()
    }

    private fun stopSlideshow() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)  // Clear all pending callbacks
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
        imageCountText.text = "Image $displayPosition of ${imageFiles.size}"
        delayText.text = "Delay: ${delayMillis / 1000}s"
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
                    handler.removeCallbacksAndMessages(null)  // Cancel pending transitions
                    showPreviousImage()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (canProcessInput() && !isTransitioning) {
                    handler.removeCallbacksAndMessages(null)  // Cancel pending transitions
                    showNextImage()
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
    }

    override fun onResume() {
        super.onResume()
        loadSettings()

        // Verify folder is still accessible (USB might have been disconnected)
        val folderPath = SlideshowPrefs.getFolderPath(this)
        val folder = java.io.File(folderPath)
        if (!folder.exists() || !folder.canRead()) {
            statusText.text = "Folder not accessible"
            statusText.visibility = View.VISIBLE
            stopSlideshow()
            return
        }

        if (isPlaying && imageFiles.isNotEmpty() && displayOrder.isNotEmpty()) {
            scheduleNextImage()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)

        // Cancel animations
        imageView1.animate().cancel()
        imageView2.animate().cancel()

        // Clear image views
        imageView1.setImageBitmap(null)
        imageView2.setImageBitmap(null)

        // Recycle bitmaps to free memory
        currentBitmap1?.recycle()
        currentBitmap1 = null
        currentBitmap2?.recycle()
        currentBitmap2 = null

        // Clear references
        imageFiles = emptyList()
        displayOrder.clear()
    }
}
