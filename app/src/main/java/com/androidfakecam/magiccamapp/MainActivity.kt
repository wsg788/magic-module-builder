package com.androidfakecam.magiccamapp

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Main activity for the Magic Cam App.
 *
 * This version implements a full app picker showing all launchable apps (system and user)
 * and improves image compression when saving selected media.  Selected apps are
 * persisted to filesDir/selected_app.txt and the most recent image or video is saved
 * to filesDir/spoof_media/current_media.jpg or current_media.mp4 for use by the Magisk module.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var pickMediaLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // register media picker
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { copyMediaToInternal(it) }
        }

        val selectMediaButton = findViewById<Button>(R.id.btn_select_media)
        selectMediaButton.setOnClickListener {
            // allow both images and videos
            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
        }

        val selectAppButton = findViewById<Button>(R.id.btn_select_app)
        selectAppButton.setOnClickListener {
            showAppPicker()
        }
    }

    /**
     * Display a picker dialog listing all apps with launcher intents.  Includes both
     * system and user applications.  Saves the selected package name to a file.
     */
    private fun showAppPicker() {
        val pm = packageManager
        // Get all installed applications that have launch intents
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val names = apps.map { it.loadLabel(pm).toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select App to Spoof")
            .setItems(names) { _, which ->
                val selected = apps[which]
                // Write the selected package name to filesDir/selected_app.txt
                val file = File(filesDir, "selected_app.txt")
                file.writeText(selected.packageName)
                Toast.makeText(this, "Selected ${names[which]}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Copy and convert the selected media into internal storage.  Images are
     * resized to 640px width and compressed to JPEG at quality 80.  Videos are
     * copied without modification but renamed to current_media.mp4.  Other files
     * are copied with generic name current_media.
     */
    private fun copyMediaToInternal(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri)
            val destDir = File(filesDir, "spoof_media")
            if (!destDir.exists()) destDir.mkdirs()

            if (mimeType != null && mimeType.startsWith("image/")) {
                // Read bitmap and scale down to 640px width
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val width = 640
                    val ratio = width.toFloat() / bitmap.width
                    val height = (bitmap.height * ratio).toInt()
                    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    val destFile = File(destDir, "current_media.jpg")
                    destFile.outputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                }
            } else if (mimeType != null && mimeType.startsWith("video/")) {
                // Copy video and rename to current_media.mp4
                val destFile = File(destDir, "current_media.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // Other file types: copy with generic name
                val destFile = File(destDir, "current_media")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Toast.makeText(this, "Media saved!", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
