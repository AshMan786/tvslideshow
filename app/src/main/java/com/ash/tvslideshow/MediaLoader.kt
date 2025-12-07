package com.ash.tvslideshow

import java.io.File

/**
 * Helper to find media files (images and videos) in a folder.
 */
object MediaLoader {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "3gp")

    val ALL_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    fun isVideo(file: File): Boolean {
        return file.extension.lowercase() in VIDEO_EXTENSIONS
    }

    fun isImage(file: File): Boolean {
        return file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    fun getMediaFiles(folderPath: String, includeSubfolders: Boolean = false): List<File> {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }

        return if (includeSubfolders) {
            getMediaFilesRecursive(folder).sortedBy { it.absolutePath.lowercase() }
        } else {
            folder.listFiles { file ->
                file.isFile && file.extension.lowercase() in ALL_EXTENSIONS
            }?.sortedBy { it.name } ?: emptyList()
        }
    }

    private fun getMediaFilesRecursive(folder: File): List<File> {
        val media = mutableListOf<File>()

        folder.listFiles()?.forEach { file ->
            when {
                file.isFile && file.extension.lowercase() in ALL_EXTENSIONS -> {
                    media.add(file)
                }
                file.isDirectory && !file.isHidden -> {
                    media.addAll(getMediaFilesRecursive(file))
                }
            }
        }

        return media
    }

    /**
     * Count media files in a folder (for UI display).
     */
    fun countMediaFiles(folderPath: String, includeSubfolders: Boolean = false): Int {
        return getMediaFiles(folderPath, includeSubfolders).size
    }
}
