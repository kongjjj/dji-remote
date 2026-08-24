package com.dimadesu.djiremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.dimadesu.djiremote.dji.DjiModel
import com.dimadesu.djiremote.dji.DjiRepository
import com.dimadesu.djiremote.dji.SettingsDjiDevice
import com.dimadesu.djiremote.dji.SettingsDjiDeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "floating_bubble_channel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService<WindowManager>()!!
        @Suppress("InflateParams")
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null)

        @Suppress("DEPRECATION")
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            LayoutParams.TYPE_PHONE
        }

        params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            layoutType,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(floatingView, params)

        val streamControlButton = floatingView.findViewById<ImageView>(R.id.stream_control_button)
        val floatingContainer = floatingView.findViewById<View>(R.id.floating_container)

        DjiRepository.initialize(this)
        var isStreaming = false
        var currentLastDevice: SettingsDjiDevice? = null

        serviceScope.launch {
            combine(DjiRepository.lastUsedDeviceId, DjiRepository.devices) { lastId, devices ->
                devices.find { it.id == lastId } ?: devices.firstOrNull()
            }.collect { deviceToUse ->
                currentLastDevice = deviceToUse
                if (deviceToUse != null) {
                    streamControlButton.visibility = View.VISIBLE
                    isStreaming = deviceToUse.isStarted || deviceToUse.state != SettingsDjiDeviceState.IDLE
                    streamControlButton.setImageResource(
                        if (isStreaming) R.drawable.ic_stop_white_45 else R.drawable.ic_play_white_45
                    )
                    // Set red background tint when streaming, default (null) when idle to use XML background
                    streamControlButton.backgroundTintList = if (isStreaming) {
                        android.content.res.ColorStateList.valueOf(0x80FF0000.toInt())
                    } else {
                        null
                    }
                } else {
                    streamControlButton.visibility = View.GONE
                }
            }
        }

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        floatingContainer.setOnTouchListener(object : View.OnTouchListener {
            private var initialX: Int = 0
            private var initialY: Int = 0
            private var initialTouchX: Float = 0f
            private var initialTouchY: Float = 0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) {
                            v.performClick()
                            // Check which button was clicked based on rawX vs view position
                            val location = IntArray(2)
                            v.getLocationOnScreen(location)
                            val clickX = event.rawX - location[0]
                            
                            if (streamControlButton.visibility == View.VISIBLE && clickX > v.width / 2) {
                                // Clicked right side (stream control)
                                currentLastDevice?.let { device ->
                                    if (isStreaming) {
                                        DjiModel.stopStreaming(device)
                                    } else {
                                        DjiModel.startStreaming(this@FloatingBubbleService, device)
                                    }
                                }
                            } else {
                                // Clicked left side or background (restore app)
                                val intent = Intent(this@FloatingBubbleService, MainActivity::class.java)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(intent)
                                stopSelf()
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isMoving = true
                        }

                        if (isMoving) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Floating Bubble"
            val importance = NotificationManager.IMPORTANCE_MIN
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService<NotificationManager>()!!
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("App Minimized")
            .setContentText("Click the bubble to restore")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
