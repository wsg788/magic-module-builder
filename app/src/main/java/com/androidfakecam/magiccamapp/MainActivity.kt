package com.androidfakecam.magiccamapp

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

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
            showAppPicker()
        }
    }

    /**
     * Data class to hold app information for the picker
     */
    data class AppEntry(val name: String, val packageName: String, val icon: Drawable)

    /**
     * Shows a picker dialog listing all installed apps (system and user) with their icons.
     */
    private fun showAppPicker() {
        val pm = packageManager
        // Retrieve all installed applications, including system apps
        val applications = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appEntries = mutableListOf<AppEntry>()
        for (appInfo in applications) {
            val name = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            appEntries.add(AppEntry(name, appInfo.packageName, icon))
        }
        // Sort alphabetically by name (case-insensitive)
        appEntries.sortBy { it.name.lowercase(Locale.getDefault()) }

        // Create a custom adapter to show icons and names in the dialog
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = appEntries.size
            override fun getItem(position: Int): Any = appEntries[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.select_dialog_item, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                val entry = appEntries[position]
                textView.text = entry.name
                // Set icon on the left
                val iconSize = textView.lineHeight
                entry.icon.setBounds(0, 0, iconSize, iconSize)
                textView.setCompoundDrawables(entry.icon, null, null, null)
                textView.compoundDrawablePadding = 16
                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Select App to Spoof")
            .setAdapter(adapter) { dialog, which ->
                val selected = appEntries[which]
                // Write the selected package name to internal storage
                try {
                    val file = File(filesDir, "selected_app.txt")
                    file.writeText(selected.packageName)
                    Toast.makeText(
                        this,
                        "Selected ${'$'}{selected.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to save app selection: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Copies the selected media into the app's internal storage directory and performs conversion:
     *  - If the file is an image, scale it down and save as JPEG.
     *  - If the file is a video, copy it as-is.
     */
    private fun copyMediaToInternal(uri: Uri) {
        try {
            val destDir = File(filesDir, "spoof_media")
            if (!destDir.exists()) destDir.mkdirs()
            // Determine MIME type to decide conversion
            val mime = contentResolver.getType(uri) ?: ""
            val destFile: File
            if (mime.startsWith("image/")) {
                // Decode image
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    // Scale the bitmap down to a reasonable size (e.g., width 640 while keeping aspect ratio)
                    val maxDim = 640
                    val ratio = minOf(
                        maxDim.toFloat() / bitmap.width,
                        maxDim.toFloat() / bitmap.height
                    )
                    val width = (bitmap.width * ratio).toInt()
                    val height = (bitmap.height * ratio).toInt()
                    val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    destFile = File(destDir, "current_media.jpg")
                    FileOutputStream(destFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }
                    scaled.recycle()
                    Toast.makeText(this, "Image saved to ${'$'}{destFile.absolutePath}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Unable to decode image", Toast.LENGTH_SHORT).show()
                    return
                }
            } else if (mime.startsWith("video/")) {
                destFile = File(destDir, "current_media.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "Video saved to ${'$'}{destFile.absolutePath}", Toast.LENGTH_SHORT).show()
            } else {
                // Unknown type; just copy with generic name
                destFile = File(destDir, "current_media")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "Media saved to ${'$'}{destFile.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to copy media: ${'$'}{e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
