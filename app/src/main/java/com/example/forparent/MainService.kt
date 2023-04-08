package com.example.forparent

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import okhttp3.*
import org.json.JSONObject
import java.io.*


class MainService: Service()  {

    private var mediaProjectionManager:MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var resultCode:Int ? = null
    private var resultData:Intent ? = null
    private var windowManager: WindowManager? = null
    private var inflater: LayoutInflater? = null
    private var recognizerIntent:Intent? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var contentText: EditText? = null
    private var screenFile:File ?= null

    companion object {
        const val NOTIFICATION_ID = 123456
        const val CHANNEL_ID = "notification"
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        init()
        initRecognizer()
        showNotification()
        showFloating()

    }

    private fun getYoutubeView():View {
        val youtubeView = inflater!!.inflate(R.layout.view_youtube, null)
        val youTubePlayerView: YouTubePlayerView = youtubeView.findViewById(R.id.youtube_player_view)
        youTubePlayerView.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                val videoId = "S0Q4gqBUs7c"

                youTubePlayer.cueVideo(videoId, 0f)
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                super.onStateChange(youTubePlayer, state)

                when(state) {
                    PlayerConstants.PlayerState.PLAYING -> {

                        val wmParams = WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT
                        )
                        wmParams.gravity = Gravity.CENTER_HORIZONTAL
                        wmParams.screenOrientation =  ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        windowManager?.updateViewLayout(youtubeView, wmParams)
                    }
                    else -> Log.i("t","")
                }
            }
        })

        return youtubeView
    }

    private fun sendVideo() {


        try {
            val client = OkHttpClient()


            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image_file",
                    screenFile!!.name,
                    RequestBody.create(MediaType.parse("image/*"), screenFile!!)
                )
                .addFormDataPart("input_text", "Hello, FastAPI!")
                .build()

            val request = Request.Builder()
                .url("http://61.42.251.241:7979/uploadfile/")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle failure
                }

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body()?.string()
                    val jsonObject = JSONObject(json)
//                    val videoUrl = jsonObject.get("videoId")
//                    val startTime = jsonObject.get("startTime")
////                     Handle responseString
//                    Log.i("ttt", videoUrl.toString() + startTime.toString())
                }
            })
        }catch (e: java.lang.Exception)
        {
            e.printStackTrace()
        }
    }

    private fun init() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

    }

    private fun initRecognizer() {
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent?.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName);
        recognizerIntent?.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR")
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
    private fun showFloating() {
        var xInitCord = 0
        var yInitCord = 0
        var xInitMargin = 0
        var yInitMargin = 0

        val floatingView = getFloatingView()

        windowManager?.let {wm ->
            val wmParams =  WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            wmParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            wm.addView(floatingView, wmParams)

            floatingView?.setOnTouchListener {_, event ->
                val layoutParams = floatingView?.layoutParams as WindowManager.LayoutParams;
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
                        wm.updateViewLayout(floatingView, layoutParams)
                    }
                    MotionEvent.ACTION_UP -> { }
                }

                return@setOnTouchListener true
            }
        }
    }

    private fun getFloatingView(): View {
        val view = inflater!!.inflate(R.layout.view_floating, null)

        view.findViewById<Button>(R.id.show_prompt_button).setOnClickListener{
            // 이미지 캡처
            captureScreen()
            showPrompt()
        }

        return view
    }

    private fun getPromptView(): View {
        val view = inflater!!.inflate(R.layout.view_prompt, null)

        view.findViewById<Button>(R.id.prompt_exit).setOnClickListener{
            windowManager?.removeView(view)
        }

        view.findViewById<Button>(R.id.start_record_audio).setOnClickListener{
            windowManager?.removeView(view)
            showRecordPrompt()
        }

        return view
    }

    private fun getRecordView(): View {
        val view = inflater!!.inflate(R.layout.view_audio_record, null)

        contentText = view.findViewById<EditText>(R.id.content_text)

        view.findViewById<Button>(R.id.exit_record_audio).setOnClickListener{
            windowManager?.removeView(view)
        }

        view.findViewById<Button>(R.id.stop_record_audio).setOnClickListener{
            // 음성 녹음 종료
            stopRecord()
            // api 호출

            Thread {
                sendVideo()
            }.start()

            showYoutube()

        }

        return view
    }

    private fun showYoutube() {
        val youtubeView =  getYoutubeView()

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.CENTER_HORIZONTAL
        windowManager?.addView(youtubeView, wmParams)
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
        windowManager?.addView(promptView, wmParams)
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
        windowManager?.addView(recordView, wmParams)

        // 음성 인식 시작
        startRecord()
    }

    private fun startRecord() {
        speechRecognizer= SpeechRecognizer.createSpeechRecognizer(applicationContext);
        speechRecognizer?.setRecognitionListener(recognitionListener())
        speechRecognizer?.startListening(recognizerIntent!!);
    }

    private fun recognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Toast.makeText(applicationContext, "음성인식 시작", Toast.LENGTH_SHORT).show()

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}

        // 말하기 시작
        override fun onBeginningOfSpeech() {}

        // 말 멈추면 호출
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
           Log.e("test", error.toString())

            // 에러 처리 필요
        }

        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

            val originText = contentText?.text

            Log.i("test", matches.toString())
            var newText = ""

            if (matches != null) {
                for(i in 0 until matches.size)
                    newText += matches[i]
            }

            contentText?.setText("$originText$newText ")
            speechRecognizer?.startListening(recognizerIntent); // 계속 녹음
        }
    }


    private fun stopRecord() {
        speechRecognizer?.stopListening();   //녹음 중지
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

