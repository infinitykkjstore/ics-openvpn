package de.blinkt.openvpn.activities

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import de.blinkt.openvpn.R

class BubbleService : Service() {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var menuView: View? = null

    override fun onCreate() {
        super.onCreate()
        if (!canDrawOverlays(this)) {
            // Service should be started only after permission granted
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createBubble()
    }

    private fun createBubble() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.START or Gravity.TOP
        layoutParams.x = 100
        layoutParams.y = 200

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        bubbleView = inflater.inflate(R.layout.bubble_icon, null)
        menuView = inflater.inflate(R.layout.bubble_menu, null)

        val icon: ImageView? = bubbleView?.findViewById(R.id.bubble_icon)

        icon?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(bubbleView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // treat as click if small movement
                        val dx = Math.abs(event.rawX - initialTouchX)
                        val dy = Math.abs(event.rawY - initialTouchY)
                        if (dx < 10 && dy < 10) {
                            toggleMenu(layoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Add bubble
        windowManager?.addView(bubbleView, layoutParams)

        // Setup menu but not added
        val btnOpen: Button? = menuView?.findViewById(R.id.btn_open_app)
        val btnDisconnect: Button? = menuView?.findViewById(R.id.btn_disconnect)
        val btnQuit: Button? = menuView?.findViewById(R.id.btn_quit)

        btnOpen?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            hideMenu()
        }

        btnDisconnect?.setOnClickListener {
            // Send intent to disconnect activity
            val i = Intent(this, DisconnectVPN::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            hideMenu()
        }

        btnQuit?.setOnClickListener {
            stopSelf()
        }

        // Close menu when touch outside
        menuView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE || event.action == MotionEvent.ACTION_CANCEL) {
                hideMenu()
                true
            } else false
        }
    }

    private fun toggleMenu(layoutParams: WindowManager.LayoutParams) {
        if (menuView?.parent == null) {
            val menuParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            // position menu next to bubble
            menuParams.gravity = Gravity.START or Gravity.TOP
            menuParams.x = layoutParams.x + 80
            menuParams.y = layoutParams.y

            // allow focus to detect outside touch
            menuParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

            windowManager?.addView(menuView, menuParams)
        } else {
            hideMenu()
        }
    }

    private fun hideMenu() {
        if (menuView?.parent != null) {
            try {
                windowManager?.removeView(menuView)
            } catch (e: Exception) {
            }
        }
    }

    override fun onDestroy() {
        try {
            if (bubbleView != null && bubbleView?.parent != null) windowManager?.removeView(bubbleView)
            if (menuView != null && menuView?.parent != null) windowManager?.removeView(menuView)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }

        fun requestPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + context.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
