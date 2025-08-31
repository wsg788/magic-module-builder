package com.androidfakecam.magiccamapp

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var pickMediaLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Register media picker launcher
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { copyMediaToInternal(it) }
        }

        // Button to pick media
        findViewById<Button>(R.id.btn_select_media).setOnClickListener {
            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
        }

        // Button to show app picker
        findViewById<Button>(R.id.btn_select_app).setOnClickListener {
            showAppPicker()
        }
    }

    /**
     * Data class representing an installed app entry with label, package name and icon.
     */
    private data class AppEntry(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable
    )

    /**
     * Show a dialog listing all launchable applications (both user and system apps).
     * Uses PackageManager queryIntentActivities with ACTION_MAIN and CATEGORY_LAUNCHER to gather apps.
     */
    private fun showAppPicker() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val apps: List<AppEntry> = resolveInfos.map { info ->
            val appInfo = info.activityInfo.applicationInfo
            AppEntry(
                appInfo.loadLabel(pm).toString(),
                appInfo.packageName,
                appInfo.loadIcon(pm)
            )
        }.sortedBy { it.name.lowercase() }

        val adapter: ListAdapter = object : BaseAdapter() {
            override fun getCount(): Int = apps.size
            override fun getItem(position: Int): Any = apps[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.select_dialog_item, parent, false)
                val iconView: ImageView = view.findViewById(android.R.id.icon)
                val textView: TextView = view.findViewById(android.R.id.text1)
                val entry = apps[position]
                iconView.setImageDrawable(entry.icon)
                textView.text = entry.name
                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Select App to Spoof")
            .setAdapter(adapter) { _, which ->
                val selected = apps[which]
                // Persist selected package name into internal storage
                val file = File(filesDir, "selected_app.txt")
                file.writeText(selected.packageName)
                Toast.makeText(this, "Selected ${selected.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Copy the selected media into the app's internal storage directory. Images will be scaled to
     * a maximum width of 640 px and compressed to 80% JPEG quality. Videos are copied directly.
     */
    private fun copyMediaToInternal(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri)
            val destDir = File(filesDir, "spoof_media")
            if (!destDir.exists()) destDir.mkdirs()

            if (mimeType != null && mimeType.startsWith("image/")) {
                // Scale image and save as JPEG
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
                // Copy video directly
                val destFile = File(destDir, "current_media.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                // Other file types: copy generically
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
