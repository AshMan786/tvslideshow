package com.ash.tvslideshow

import java.io.File

/**
 * Simple helper to find image files in a folder.
 */
object ImageLoader {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    fun getImageFiles(folderPath: String, includeSubfolders: Boolean = false): List<File> {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return emptyList()
        }

        return if (includeSubfolders) {
            getImageFilesRecursive(folder).sortedBy { it.absolutePath.lowercase() }
        } else {
            folder.listFiles { file ->
                file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS
            }?.sortedBy { it.name } ?: emptyList()
        }
    }

    private fun getImageFilesRecursive(folder: File): List<File> {
        val images = mutableListOf<File>()

        folder.listFiles()?.forEach { file ->
            when {
                file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS -> {
                    images.add(file)
                }
                file.isDirectory && !file.isHidden -> {
                    images.addAll(getImageFilesRecursive(file))
                }
            }
        }

        return images
    }
}
