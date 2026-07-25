package com.selfie.data.gallery

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryRepository(private val context: Context) {

    suspend fun getPhotos(folderUri: Uri?): List<Uri> = withContext(Dispatchers.IO) {
        if (folderUri == null) return@withContext emptyList()

        val folder = DocumentFile.fromTreeUri(context, folderUri)
        if (folder == null || !folder.isDirectory) return@withContext emptyList()

        folder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".jpg", ignoreCase = true) == true }
            .sortedByDescending { it.lastModified() }
            .map { it.uri }
    }
}
