package com.androidfakecam.magiccamapp

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.androidfakecam.magiccamapp.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val selectMediaButton = findViewById<Button>(R.id.btn_select_media)
        val selectAppButton = findViewById<Button>(R.id.btn_select_app)

        selectMediaButton.setOnClickListener {
            // TODO: implement media picker
        }

        selectAppButton.setOnClickListener {
            // TODO: implement app picker
        }
    }
}
