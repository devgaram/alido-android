package com.example.forparent

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.Button
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream


class MainService: Service() {

    private var mediaProjectionManager:MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var resultCode:Int ? = null
    private var resultData:Intent ? = null
    private var windowManager: WindowManager? = null

    companion object {
        const val NOTIFICATION_ID = 123456
        const val CHANNEL_ID = "notification"
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        showNotification()
        showWindow()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        resultCode = intent?.getIntExtra("resultCode", 0)
        resultData = intent?.getParcelableExtra("resultData" )

        return START_NOT_STICKY
    }

    private fun showNotification() {
        createNotificationChannel()

        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("둥실이와 함께하는 중")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setWhen(System.currentTimeMillis())
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showWindow() {
        var xInitCord: Int = 0
        var yInitCord: Int = 0
        var xInitMargin: Int = 0
        var yInitMargin: Int = 0

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.view_window, null)

        view.findViewById<Button>(R.id.capture_button).setOnClickListener{
            captureScreen()
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.let {wm ->
            val wmParams =  WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            wmParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            wm.addView(view, wmParams)

            view?.setOnTouchListener {_, event ->
                val layoutParams = view?.layoutParams as WindowManager.LayoutParams;
                val xCord = event.rawX.toInt()
                val yCord = event.rawY.toInt()
                val xCordDestination: Int
                val yCordDestination: Int

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        xInitCord = xCord
                        yInitCord = yCord
                        xInitMargin = layoutParams.x
                        yInitMargin = layoutParams.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val xDiffMove: Int = xCord - xInitCord
                        val yDiffMove: Int = yCord - yInitCord
                        xCordDestination = xInitMargin + xDiffMove
                        yCordDestination = yInitMargin + yDiffMove
                        layoutParams.x = xCordDestination
                        layoutParams.y = yCordDestination
                        wm.updateViewLayout(view, layoutParams)
                    }
                    MotionEvent.ACTION_UP -> { }
                }

                return@setOnTouchListener true
            }
        }
    }

    @SuppressLint("WrongConstant")
    private fun captureScreen() {
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode!!, resultData!!)

        mediaProjection?.let {mp ->
            val width = getWindowWidth()
            val height = getWindowHeight()
            val density = getDensityDpi()
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 10)
            imageReader.setOnImageAvailableListener(ImageReader.OnImageAvailableListener {reader ->
                var image: Image? = null

                try {
                    image = reader.acquireLatestImage()

                    if (image != null) {
                        val buffer = image.planes[0].buffer
                        val pixelStride = image.planes[0].pixelStride
                        val rowStride = image.planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        var outputStream: OutputStream? = null;
                        try {
                            // 파일 객체 생성
                            val filePath = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString() + "/screen")
                            filePath.mkdirs()

                            val file = File(filePath, System.currentTimeMillis().toString() + ".jpg")
                            if (file.exists()) file.delete()

                            // 비트맵 추출 -> 파일 flush
                            outputStream = FileOutputStream(file);

                            var bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
                            bitmap.copyPixelsFromBuffer(buffer)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                        } finally {
                            outputStream?.close()
                        }
                    }
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                } finally {
                    if (image != null) {
                        image?.close()
                        reader.close()
                        mp.stop()
                    }
                }

            }, null)

            imageReader?.let { reader ->
                mp.createVirtualDisplay("ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null); }
        }
    }

    private fun getWindowWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.let {wm ->
                val windowMetrics: WindowMetrics = wm.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                return windowMetrics.bounds.width() - insets.left - insets.right
            }?: 0
        } else {
            windowManager?.let { wm ->
                val displayMetrics = DisplayMetrics()
                wm.getDefaultDisplay().getMetrics(displayMetrics)
                displayMetrics.widthPixels
            }?: 0

        }
    }

    private fun getWindowHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.let {wm ->
                val windowMetrics: WindowMetrics = wm.currentWindowMetrics
                val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                return windowMetrics.bounds.height() - insets.bottom - insets.top
            }?: 0
        } else {
            windowManager?.let { wm ->
                val displayMetrics = DisplayMetrics()
                wm.getDefaultDisplay().getMetrics(displayMetrics)
                displayMetrics.heightPixels
            }?: 0

        }
    }

    private fun getDensityDpi(): Int {
       return windowManager?.let { wm ->
            val displayMetrics = DisplayMetrics()
            wm.getDefaultDisplay().getMetrics(displayMetrics)
            displayMetrics.densityDpi
        }?: 0
    }

}

