package com.androidfakecam.magiccamapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var pickMediaLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register the launcher for picking photo or video
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                // Persist permission so we can read the file later
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                copyMediaToInternal(it)
            }
        }

        val selectMediaButton = findViewById<Button>(R.id.btn_select_media)
        selectMediaButton.setOnClickListener {
            // Launch picker allowing both images and videos
            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
        }

        val selectAppButton = findViewById<Button>(R.id.btn_select_app)
        selectAppButton.setOnClickListener {
            // TODO: implement app picker in a future dev build
            Toast.makeText(this, "App picker not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyMediaToInternal(uri: Uri) {
        try {
            val destDir = File(filesDir, "spoof_media")
            if (!destDir.exists()) destDir.mkdirs()
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "selected_media"
            val destFile = File(destDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "Saved to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to copy media: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
