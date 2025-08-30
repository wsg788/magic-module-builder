package com.androidfakecam.magiccamapp

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Dev Build 6 MainActivity
 *
 * This activity implements a media picker and a full app picker listing all launchable apps (system and user)
 * along with their icons. Selected media is resized or copied to current_media.jpg/mp4 and the selected app
 * package name is written to selected_app.txt for the Magisk module to read.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var pickMediaLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // register launcher for picking image or video
        pickMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handlePickedMedia(it) }
        }

        val selectMediaButton = findViewById<Button>(R.id.btn_select_media)
        selectMediaButton.setOnClickListener {
            pickMediaLauncher.launch(arrayOf("image/*", "video/*"))
        }

        val selectAppButton = findViewById<Button>(R.id.btn_select_app)
        selectAppButton.setOnClickListener {
            showAppPicker()
        }
    }

    private fun showAppPicker() {
        // query all launchable activities (both system and user apps)
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        val appEntries = activities.map {
            val appInfo = it.activityInfo.applicationInfo
            val label = it.loadLabel(packageManager).toString()
            val icon = it.loadIcon(packageManager)
            AppEntry(label, appInfo.packageName, icon)
        }.sortedBy { it.name.lowercase(Locale.getDefault()) }

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = appEntries.size
            override fun getItem(position: Int): Any = appEntries[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = convertView ?: layoutInflater.inflate(android.R.layout.select_dialog_item, parent, false)
                val entry = appEntries[position]
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.text = entry.name
                textView.setCompoundDrawablesWithIntrinsicBounds(entry.icon, null, null, null)
                textView.compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.app_picker_icon_padding)
                return view
            }
        }

        AlertDialog.Builder(this, R.style.Theme_Material3_Dialog)
            .setTitle(R.string.select_app_to_spoof)
            .setAdapter(adapter) { dialog, which ->
                val selected = appEntries[which]
                // save selected package name
                saveSelectedApp(selected.packageName)
                Toast.makeText(this, getString(R.string.app_selected_message, selected.name), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveSelectedApp(packageName: String) {
        val file = File(filesDir, "selected_app.txt")
        file.writeText(packageName)
    }

    private fun handlePickedMedia(uri: Uri) {
        try {
            val mimeType = contentResolver.getType(uri) ?: ""
            val destDir = File(filesDir, "spoof_media").apply { if (!exists()) mkdirs() }
            val destFile: File = when {
                mimeType.startsWith("image/") -> {
                    val bmp = (contentResolver.openInputStream(uri)?.use { stream ->
                        (android.graphics.drawable.Drawable.createFromStream(stream, null) as BitmapDrawable).bitmap
                    }) ?: run {
                        Toast.makeText(this, R.string.failed_to_read_image, Toast.LENGTH_SHORT).show(); return
                    }
                    val scaled = scaleBitmapDown(bmp, 640)
                    val file = File(destDir, "current_media.jpg")
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }
                    file
                }
                mimeType.startsWith("video/") -> {
                    val file = File(destDir, "current_media.mp4")
                    copyStream(contentResolver.openInputStream(uri), FileOutputStream(file))
                    file
                }
                else -> {
                    val name = getFileName(uri) ?: "current_media"
                    val file = File(destDir, name)
                    copyStream(contentResolver.openInputStream(uri), FileOutputStream(file))
                    file
                }
            }
            Toast.makeText(this, getString(R.string.media_saved_message, destFile.name), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.failed_to_copy_media, e.localizedMessage), Toast.LENGTH_SHORT).show()
        }
    }

    private fun scaleBitmapDown(bmp: Bitmap, maxSize: Int): Bitmap {
        val width = bmp.width
        val height = bmp.height
        val ratio = if (width > height) maxSize.toFloat() / width else maxSize.toFloat() / height
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bmp, newWidth, newHeight, true)
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            val path = uri.path
            val cut = path?.lastIndexOf('/') ?: -1
            if (cut != -1) name = path?.substring(cut + 1)
        }
        return name
    }

    private fun copyStream(input: InputStream?, output: OutputStream?) {
        if (input == null || output == null) return
        input.use { i ->
            output.use { o ->
                val buf = ByteArray(4096)
                var len: Int
                while (i.read(buf).also { len = it } > 0) {
                    o.write(buf, 0, len)
                }
            }
        }
    }

    data class AppEntry(val name: String, val packageName: String, val icon: android.graphics.drawable.Drawable)
}
