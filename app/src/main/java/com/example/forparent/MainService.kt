package com.example.forparent

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.*


class MainService : Service() {
    private val windowManager by lazy {
        getSystemService(WINDOW_SERVICE) as WindowManager
    }

    private val inflater by lazy {
        getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
    }

    private val recognizerIntent by lazy {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
    }
    private var resultCode: Int? = null
    private var resultData: Intent? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechText: String = ""
    private var screenFile: File? = null
    private var packageName: String = ""

    private var videoId: String? = null
    private var startTime: Float? = 0f



    companion object {
        const val NOTIFICATION_ID = 123456
        const val CHANNEL_ID = "notification"
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        resultCode = intent?.getIntExtra("resultCode", 0)
        resultData = intent?.getParcelableExtra("resultData")


        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        showNotification()
        showFloating()

    }

    private fun showNotification() {
        createNotificationChannel()

        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("알리도와 함께하는 중")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setWhen(System.currentTimeMillis())
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloating() {
        var xInitCord = 0
        var yInitCord = 0
        var xInitMargin = 0
        var yInitMargin = 0

        val floatingView = getFloatingView()
        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        windowManager.addView(floatingView, wmParams)


        floatingView.setOnTouchListener { _, event ->
            val layoutParams = floatingView.layoutParams as WindowManager.LayoutParams;
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
                    windowManager.updateViewLayout(floatingView, layoutParams)
                }
                MotionEvent.ACTION_UP -> {
                }
            }

            return@setOnTouchListener true

        }
    }

    private fun getFloatingView(): View {
        val view = inflater.inflate(R.layout.view_floating, null)

        view.findViewById<Button>(R.id.show_prompt_button).setOnClickListener {
            captureScreen()
            showPrompt()
        }

        return view
    }

    private fun showPrompt() {
        val promptView = getPromptView()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER_HORIZONTAL
        windowManager.addView(promptView, wmParams)
    }

    private fun getPromptView(): View {
        val view = inflater.inflate(R.layout.view_prompt, null)

        view.findViewById<Button>(R.id.prompt_exit).setOnClickListener {
            windowManager.removeView(view)
        }

        view.findViewById<Button>(R.id.start_record_audio).setOnClickListener {
            windowManager.removeView(view)
            showRecordPrompt()
        }

        return view
    }

    private fun showRecordPrompt() {
        val recordView = getRecordView()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER_HORIZONTAL
        windowManager.addView(recordView, wmParams)

        // 음성 인식 시작
        startRecord()
    }

    private fun getYoutubeView(): View {
        val view = inflater.inflate(R.layout.view_youtube, null)
        val landView = inflater.inflate(R.layout.view_youtube_landscape, null)

        val playerView: YouTubePlayerView = view.findViewById(R.id.youtube_player_view)
        val playerLandView: YouTubePlayerView = landView.findViewById(R.id.youtube_player_view_landscape)

        playerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.cueVideo(videoId!!, startTime!!)
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                super.onStateChange(youTubePlayer, state)

                when (state) {
                    PlayerConstants.PlayerState.UNSTARTED -> {

                        val wmParams = WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT
                        )

                        wmParams.gravity = Gravity.CENTER_HORIZONTAL
                        wmParams.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        windowManager.addView(landView, wmParams)
                        windowManager.removeView(view)
                    }
                    else -> Log.i("t", "")
                }
            }
        })

        playerLandView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                youTubePlayer.loadVideo(videoId!!, startTime!!)
            }
        })

        view.findViewById<Button>(R.id.youtube_exit_button).setOnClickListener {
            windowManager.removeView(view)
        }

        landView.findViewById<Button>(R.id.youtube_exit_button_landscape).setOnClickListener {
            windowManager.removeView(landView)
        }

        return view
    }

    private fun getJsonFromServer(): String {

        val client = OkHttpClient()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image_file",
                screenFile!!.name,
                RequestBody.create(MediaType.parse("image/*"), screenFile!!)
            )
            .addFormDataPart("input_text", speechText)
            .addFormDataPart("package_name", packageName)
            .build()

        val request = Request.Builder()
            .url("http://61.42.251.241:7979/uploadfile/")
            .post(requestBody)
            .build()


        client.newCall(request).execute().use { response ->
            return if (response.body() != null) {
                response.body()!!.string()

            } else {
                "body is null"
            }
        }

    }


    private fun sendVideo() {
        CoroutineScope(Dispatchers.Main).launch {

            val loadingView = showLoadingView()

            val result = CoroutineScope(Dispatchers.IO).async {
                getJsonFromServer()
            }.await()


            windowManager.removeView(loadingView)

            val jsonObject = JSONObject(result)
            videoId = jsonObject.getString("videoId")
            startTime = jsonObject.getInt("startTime").toFloat()


            if (videoId != null) {
                showYoutube()
            } else {
                showNoAudioView()
            }

        }

    }

    private fun showLoadingView(): View {
        val loadingView = getLoadingView()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER_HORIZONTAL
        windowManager.addView(loadingView, wmParams)

        return loadingView
    }

    private fun getLoadingView(): View {
        return inflater.inflate(R.layout.view_loading, null)
    }

    private fun showNoAudioView() {
        val noAudioView = getNoAudioView()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER_HORIZONTAL
        windowManager.addView(noAudioView, wmParams)
    }

    private fun getNoAudioView(): View{
        val noAudioView = inflater.inflate(R.layout.view_no_audio, null)


        noAudioView.findViewById<Button>(R.id.no_record_exit).setOnClickListener {
            windowManager.removeView(noAudioView)
        }

        noAudioView.findViewById<Button>(R.id.restart_record).setOnClickListener {
            windowManager.removeView(noAudioView)
            startRecord()
            showRecordPrompt()
        }

        return noAudioView
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


    private fun getRecordView(): View {
        val view = inflater.inflate(R.layout.view_audio_record, null)

        view.findViewById<Button>(R.id.exit_record_audio).setOnClickListener {
            windowManager.removeView(view)
        }

        view.findViewById<Button>(R.id.stop_record_audio).setOnClickListener {
            stopRecord()
            windowManager.removeView(view)

            Log.i("test", speechText)
            if (speechText.isEmpty()) {
                showNoAudioView()
            } else {
                sendVideo()
            }

        }

        return view
    }

    private fun showYoutube() {
        val youtubeView = getYoutubeView()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER_HORIZONTAL
        windowManager.addView(youtubeView, wmParams)
    }


    private fun startRecord() {
        speechText = ""
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext);
        speechRecognizer?.setRecognitionListener(recognitionListener())
        speechRecognizer?.startListening(recognizerIntent);
    }

    private fun recognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) =
            Toast.makeText(applicationContext, "음성인식 중..", Toast.LENGTH_SHORT).show()

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

        // 말하기 시작
        override fun onBeginningOfSpeech() {}

        // 말 멈추면 호출
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {

            // 에러 처리 필요
        }

        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            if (matches != null) {
                for (i in 0 until matches.size)
                    speechText += matches[i]
            }

            speechRecognizer?.startListening(recognizerIntent); // 계속 녹음
        }
    }


    private fun stopRecord() {
        speechRecognizer?.stopListening();   //녹음 중지
    }

    @SuppressLint("WrongConstant")
    private fun captureScreen() {

        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode!!, resultData!!)
        mediaProjection?.let { mp ->
            val width = getWindowWidth()
            val height = getWindowHeight()
            val density = getDensityDpi()
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 10)
            imageReader.setOnImageAvailableListener(ImageReader.OnImageAvailableListener { reader ->
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
                            val filePath = File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                                    .toString() + "/screen"
                            )
                            filePath.mkdirs()

                            val file =
                                File(filePath, System.currentTimeMillis().toString() + ".jpg")
                            if (file.exists()) file.delete()

                            // 비트맵 추출 -> 파일 flush
                            outputStream = FileOutputStream(file);

                            var bitmap = Bitmap.createBitmap(
                                width + rowPadding / pixelStride,
                                height,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                            screenFile = file

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
                mp.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
                );
            }
        }
    }

    private fun getWindowWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.let { wm ->
                val windowMetrics: WindowMetrics = wm.currentWindowMetrics
                val insets =
                    windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                return windowMetrics.bounds.width() - insets.left - insets.right
            } ?: 0
        } else {
            windowManager?.let { wm ->
                val displayMetrics = DisplayMetrics()
                wm.getDefaultDisplay().getMetrics(displayMetrics)
                displayMetrics.widthPixels
            } ?: 0

        }
    }

    private fun getWindowHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager?.let { wm ->
                val windowMetrics: WindowMetrics = wm.currentWindowMetrics
                val insets =
                    windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                return windowMetrics.bounds.height() - insets.bottom - insets.top
            } ?: 0
        } else {
            windowManager?.let { wm ->
                val displayMetrics = DisplayMetrics()
                wm.getDefaultDisplay().getMetrics(displayMetrics)
                displayMetrics.heightPixels
            } ?: 0

        }
    }

    private fun getDensityDpi(): Int {
        return windowManager?.let { wm ->
            val displayMetrics = DisplayMetrics()
            wm.getDefaultDisplay().getMetrics(displayMetrics)
            displayMetrics.densityDpi
        } ?: 0
    }


}

