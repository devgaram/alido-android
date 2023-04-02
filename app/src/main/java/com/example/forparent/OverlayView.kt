package com.example.forparent

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.RelativeLayout


@SuppressLint("ClickableViewAccessibility")
class OverlayView @JvmOverloads constructor (
    context:Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
): RelativeLayout(context, attrs, defStyleAttr) {
    private val windowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var xInitCord: Int = 0
    private var yInitCord: Int = 0
    private var xInitMargin: Int = 0
    private var yInitMargin: Int = 0


    init {
        val wmParams =  WindowManager.LayoutParams(
           WindowManager.LayoutParams.WRAP_CONTENT,
           WindowManager.LayoutParams.WRAP_CONTENT,
           WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
           WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
           PixelFormat.TRANSLUCENT
        )
        wmParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

        windowManager.addView(this, wmParams)

        setOnTouchListener {_, event ->
            val layoutParams = layoutParams as WindowManager.LayoutParams

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
                    windowManager.updateViewLayout(this, layoutParams)
                }
                MotionEvent.ACTION_UP -> { }
            }
            return@setOnTouchListener true
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    override fun onLayout(p0: Boolean, p1: Int, p2: Int, p3: Int, p4: Int) {
       super.onLayout(p0, p1, p2, p3, p4)
    }

}