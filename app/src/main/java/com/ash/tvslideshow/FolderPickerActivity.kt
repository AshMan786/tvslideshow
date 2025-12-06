package com.ash.tvslideshow

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.ComponentActivity
import java.io.File

/**
 * Simple folder browser for selecting image folder.
 * Navigate with D-pad, select folders to enter, back to go up.
 * Shows all storage volumes including USB drives.
 */
class FolderPickerActivity : ComponentActivity() {

    companion object {
        const val RESULT_PATH = "selected_path"
    }

    private lateinit var pathText: TextView
    private lateinit var listView: ListView
    private lateinit var selectButton: Button
    private lateinit var imageCountText: TextView

    private var currentPath: File? = null  // null = show storage selection
    private var folders: List<File> = emptyList()
    private var storageVolumes: List<StorageInfo> = emptyList()

    data class StorageInfo(val name: String, val path: File)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folder_picker)

        pathText = findViewById(R.id.current_path_text)
        listView = findViewById(R.id.folder_list)
        selectButton = findViewById(R.id.select_folder_button)
        imageCountText = findViewById(R.id.image_count_text)

        // Detect available storage volumes
        storageVolumes = getStorageVolumes()

        // Start at storage selection (null) unless we have a valid start path
        val startPath = intent.getStringExtra("start_path")
        if (!startPath.isNullOrEmpty()) {
            val startFile = File(startPath)
            if (startFile.exists() && startFile.isDirectory) {
                currentPath = startFile
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            handleItemClick(position)
        }

        selectButton.setOnClickListener {
            currentPath?.let { path ->
                val intent = Intent()
                intent.putExtra(RESULT_PATH, path.absolutePath)
                setResult(Activity.RESULT_OK, intent)
                finish()
            }
        }

        refreshList()
    }

    private fun getStorageVolumes(): List<StorageInfo> {
        val volumes = mutableListOf<StorageInfo>()
        val addedPaths = mutableSetOf<String>()

        // Use StorageManager API for Android 7+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolumes = storageManager.storageVolumes
                for (volume in storageVolumes) {
                    val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        // Use reflection for older APIs
                        try {
                            val getPath = volume.javaClass.getMethod("getPath")
                            File(getPath.invoke(volume) as String)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (path != null && path.exists() && path.canRead()) {
                        val name = volume.getDescription(this) ?: path.name
                        if (addedPaths.add(path.absolutePath)) {
                            volumes.add(StorageInfo(name, path))
                        }
                    }
                }
            } catch (e: Exception) {
                // Fall through to manual detection
            }
        }

        // Internal storage (fallback)
        val internal = Environment.getExternalStorageDirectory()
        if (internal.exists() && internal.canRead() && addedPaths.add(internal.absolutePath)) {
            volumes.add(StorageInfo("Internal Storage", internal))
        }

        // Check /storage for additional volumes
        checkDirectory(File("/storage"), volumes, addedPaths,
            excludeNames = setOf("self", "emulated"))

        // Check common mount points for USB drives
        val usbMountPoints = listOf(
            "/mnt/media_rw",
            "/mnt/usb",
            "/mnt/usb_storage",
            "/storage/usb",
            "/storage/usb0",
            "/storage/usb1",
            "/storage/usb2",
            "/storage/UsbDriveA",
            "/storage/UsbDriveB",
            "/storage/external_storage",
            "/sdcard/usb",
            "/data/media/usb"
        )

        for (mountPoint in usbMountPoints) {
            val dir = File(mountPoint)
            if (dir.exists() && dir.canRead()) {
                if (dir.isDirectory) {
                    // Check if it's a direct mount or contains subdirectories
                    val files = dir.listFiles()
                    if (files != null && files.isNotEmpty()) {
                        // Has subdirectories - add each as a volume
                        checkDirectory(dir, volumes, addedPaths)
                    } else if (addedPaths.add(dir.absolutePath)) {
                        // Direct mount point
                        volumes.add(StorageInfo("USB Storage (${dir.name})", dir))
                    }
                }
            }
        }

        // Also scan /mnt for any mounted drives
        checkDirectory(File("/mnt"), volumes, addedPaths,
            excludeNames = setOf("runtime", "user", "secure", "asec", "obb", "fuse"))

        return volumes
    }

    private fun checkDirectory(
        dir: File,
        volumes: MutableList<StorageInfo>,
        addedPaths: MutableSet<String>,
        excludeNames: Set<String> = emptySet()
    ) {
        if (!dir.exists() || !dir.canRead()) return

        try {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.canRead()
                    && !file.name.startsWith(".")
                    && !excludeNames.contains(file.name.lowercase())) {

                    if (addedPaths.add(file.absolutePath)) {
                        val name = when {
                            file.name.contains("usb", ignoreCase = true) -> "USB Drive (${file.name})"
                            file.name.contains("sd", ignoreCase = true) &&
                                !file.absolutePath.contains("emulated") -> "SD Card (${file.name})"
                            file.absolutePath.startsWith("/storage/") -> "Storage (${file.name})"
                            else -> "External (${file.name})"
                        }
                        volumes.add(StorageInfo(name, file))
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore permission errors
        }
    }

    private fun handleItemClick(position: Int) {
        if (currentPath == null) {
            // We're in storage selection view
            if (position in storageVolumes.indices) {
                currentPath = storageVolumes[position].path
                refreshList()
            }
        } else {
            // We're in folder browsing view
            if (position == 0) {
                // First item is always navigation (go up or back to storage)
                navigateUp()
            } else {
                val index = position - 1  // Adjust for the navigation item
                if (index in folders.indices) {
                    currentPath = folders[index]
                    refreshList()
                }
            }
        }
    }

    private fun isAtStorageRoot(): Boolean {
        val path = currentPath ?: return true
        return storageVolumes.any { it.path.absolutePath == path.absolutePath }
    }

    private fun navigateUp() {
        val path = currentPath ?: return

        if (isAtStorageRoot()) {
            // Go back to storage selection
            currentPath = null
            refreshList()
        } else {
            val parent = path.parentFile
            if (parent != null && parent.canRead()) {
                currentPath = parent
                refreshList()
            } else {
                // Can't read parent, go to storage selection
                currentPath = null
                refreshList()
            }
        }
    }

    private fun refreshList() {
        val path = currentPath

        if (path == null) {
            // Show storage selection
            showStorageSelection()
            return
        }

        pathText.text = path.absolutePath

        // Count images in current folder
        try {
            val imageCount = ImageLoader.getImageFiles(path.absolutePath).size
            imageCountText.text = "$imageCount images in this folder"
        } catch (e: Exception) {
            imageCountText.text = "Cannot read folder"
        }

        // Get subfolders
        folders = try {
            path.listFiles { file -> file.isDirectory && !file.isHidden }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        // Build list items
        val items = mutableListOf<String>()

        // Always show navigation option as first item
        items.add(if (isAtStorageRoot()) "< Storage Locations" else ".. (Go Up)")

        folders.forEach { items.add(it.name) }

        if (folders.isEmpty()) {
            items.add("(No subfolders)")
        }

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.textSize = 20f
                textView.setPadding(32, 24, 32, 24)

                // Style the navigation and empty items differently
                val text = getItem(position) ?: ""
                when {
                    text.startsWith("..") || text.startsWith("<") -> textView.setTextColor(0xFF4FC3F7.toInt())
                    text.startsWith("(") -> textView.setTextColor(0xFF888888.toInt())
                    else -> textView.setTextColor(0xFFFFFFFF.toInt())
                }
                return view
            }

            override fun isEnabled(position: Int): Boolean {
                val text = getItem(position) ?: ""
                return !text.startsWith("(")  // Disable "(No subfolders)" item
            }
        }
        listView.adapter = adapter

        // Enable select button when browsing folders
        selectButton.isEnabled = true
        selectButton.alpha = 1.0f

        // Focus the list
        if (items.isNotEmpty()) {
            listView.requestFocus()
        } else {
            selectButton.requestFocus()
        }
    }

    private fun showStorageSelection() {
        pathText.text = "Select Storage Location"
        imageCountText.text = "${storageVolumes.size} storage location(s) found"

        val items = storageVolumes.map { "${it.name}\n${it.path.absolutePath}" }

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.textSize = 18f
                textView.setPadding(32, 20, 32, 20)
                textView.setTextColor(0xFFFFFFFF.toInt())
                textView.setLineSpacing(4f, 1f)
                return view
            }
        }
        listView.adapter = adapter

        if (items.isNotEmpty()) {
            listView.requestFocus()
        }

        // Disable select button when in storage selection
        selectButton.isEnabled = false
        selectButton.alpha = 0.5f
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentPath != null) {
            navigateUp()
        } else {
            super.onBackPressed()
        }
    }
}
