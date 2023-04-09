package com.example.forparent

import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri


class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionResult: ActivityResult

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAudioPermission()
        checkStoragePermission()
        checkMediaProjectPermission()


        val startButton = findViewById<Button>(R.id.start_button)
        startButton.setOnClickListener {
            checkWindowPermission()
        }
    }


    private fun checkAudioPermission() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.RECORD_AUDIO
                    ), 1
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private val overlayActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startMainService()
        }
    }

    private fun checkWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays((this))) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()
                )
                overlayActivityResult.launch(intent)
            } else {
                // 이미 권한 허용한 경우
                startMainService()
            }
        }
    }

    private fun startMainService() {
        val serviceIntent = Intent(this, MainService::class.java)
        serviceIntent.putExtra("resultCode", mediaProjectionResult.resultCode)
        serviceIntent.putExtra("resultData", mediaProjectionResult.data)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Home 이동
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)

    }

    private val mediaProjectionActivityResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK) {
            mediaProjectionResult = it
        }
    }

    private fun checkMediaProjectPermission() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjectionActivityResult.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun checkStoragePermission() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) !== PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) !== PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    ), 300
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}