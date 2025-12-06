package com.ash.tvslideshow

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Settings screen - the landing page where user configures folder path and delay.
 */
class MainActivity : ComponentActivity() {

    private lateinit var folderPathText: TextView
    private lateinit var browseButton: Button
    private lateinit var delayEdit: EditText
    private lateinit var subfoldersCheckbox: CheckBox
    private lateinit var startButton: Button
    private lateinit var statusText: TextView

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra(FolderPickerActivity.RESULT_PATH)
            if (!path.isNullOrEmpty()) {
                SlideshowPrefs.setFolderPath(this, path)
                updateUI()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            openFolderPicker()
        } else {
            Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show()
        }
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        folderPathText = findViewById(R.id.folder_path_text)
        browseButton = findViewById(R.id.browse_button)
        delayEdit = findViewById(R.id.delay_edit)
        subfoldersCheckbox = findViewById(R.id.subfolders_checkbox)
        startButton = findViewById(R.id.start_button)
        statusText = findViewById(R.id.status_text)

        // Load saved settings
        delayEdit.setText(SlideshowPrefs.getDelaySeconds(this).toString())
        subfoldersCheckbox.isChecked = SlideshowPrefs.getIncludeSubfolders(this)

        // Save checkbox state when changed
        subfoldersCheckbox.setOnCheckedChangeListener { _, isChecked ->
            SlideshowPrefs.setIncludeSubfolders(this, isChecked)
            updateUI()
        }

        browseButton.setOnClickListener {
            checkPermissionsAndBrowse()
        }

        startButton.setOnClickListener {
            saveDelay()
            startSlideshow()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    /**
     * Check if we have storage permission
     */
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkPermissionsAndBrowse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ needs MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                openFolderPicker()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                    Toast.makeText(this, "Please grant file access permission", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Unable to open settings. Please grant storage permission manually.", Toast.LENGTH_LONG).show()
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                openFolderPicker()
            } else {
                permissionLauncher.launch(arrayOf(permission))
            }
        } else {
            // Android 5 and below
            openFolderPicker()
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(this, FolderPickerActivity::class.java)
        val currentPath = SlideshowPrefs.getFolderPath(this)
        if (currentPath.isNotEmpty()) {
            intent.putExtra("start_path", currentPath)
        }
        folderPickerLauncher.launch(intent)
    }

    private fun saveDelay() {
        val delay = delayEdit.text.toString().toIntOrNull() ?: 5
        SlideshowPrefs.setDelaySeconds(this, delay.coerceIn(1, 300))
    }

    private fun startSlideshow() {
        if (!hasStoragePermission()) {
            statusText.text = "Storage permission required"
            Toast.makeText(this, "Please grant storage permission first", Toast.LENGTH_SHORT).show()
            return
        }

        val path = SlideshowPrefs.getFolderPath(this)
        if (path.isEmpty()) {
            statusText.text = "Please select a folder first"
            return
        }

        try {
            val includeSubfolders = SlideshowPrefs.getIncludeSubfolders(this)
            val imageCount = ImageLoader.getImageFiles(path, includeSubfolders).size
            if (imageCount == 0) {
                statusText.text = "No images found in selected folder"
                return
            }

            val intent = Intent(this, SlideshowActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            statusText.text = "Error accessing folder"
            Toast.makeText(this, "Cannot access folder. Check permissions.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        // Check permission status first
        if (!hasStoragePermission()) {
            folderPathText.text = "Permission required"
            statusText.text = "Tap Browse to grant storage access"
            return
        }

        val path = SlideshowPrefs.getFolderPath(this)
        if (path.isEmpty()) {
            folderPathText.text = "No folder selected"
            statusText.text = "Select a folder containing images"
        } else {
            folderPathText.text = path
            try {
                val includeSubfolders = SlideshowPrefs.getIncludeSubfolders(this)
                val imageCount = ImageLoader.getImageFiles(path, includeSubfolders).size
                statusText.text = if (imageCount > 0) "Found $imageCount images" else "No images found"
            } catch (e: Exception) {
                statusText.text = "Cannot access folder"
            }
        }
    }
}
