package com.sermonprep.app.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DetectedModel(val path: String, val name: String, val sizeBytes: Long)

/**
 * Looks for on-device LLM model files that are already on the phone (Downloads, Documents, etc.).
 *
 * Limits imposed by Android, not by this app:
 *  - Other apps' private folders (e.g. /Android/data/<other app>/) cannot be read by any app, so a
 *    model that lives only inside another app (such as Google AI Edge Gallery) is not detectable.
 *  - Android 11+ needs the "All files access" permission to see model files in shared storage.
 *    That permission is fine for an APK you install yourself but is restricted on Google Play.
 */
@Singleton
class ModelScanner @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    suspend fun scan(): List<DetectedModel> = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext emptyList()
        val found = ArrayList<DetectedModel>()

        fun walk(dir: File, depth: Int) {
            if (depth > MAX_DEPTH) return
            val children = dir.listFiles() ?: return
            for (f in children) {
                if (f.isDirectory) {
                    if (f.name.startsWith(".") || f.name == "Android") continue
                    walk(f, depth + 1)
                } else if (f.extension.lowercase() in EXTENSIONS && f.length() >= MIN_BYTES) {
                    found += DetectedModel(f.absolutePath, f.name, f.length())
                }
            }
        }

        @Suppress("DEPRECATION")
        walk(Environment.getExternalStorageDirectory(), 0)
        // Gemma 3n first, then by name.
        found.sortedWith(compareByDescending<DetectedModel> { it.name.contains("gemma-3n", true) || it.name.contains("gemma3n", true) }
            .thenBy { it.name.lowercase() })
    }

    companion object {
        private val EXTENSIONS = setOf("task", "litertlm")
        private const val MIN_BYTES = 100L * 1024 * 1024   // ignore small files that are not LLMs
        private const val MAX_DEPTH = 4
    }
}
