package io.github.gitjacobbit.whoscalling.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import io.github.gitjacobbit.whoscalling.R
import io.github.gitjacobbit.whoscalling.container

/**
 * Keeps the process alive while a call is ringing and watches the call state
 * to collapse the overlay on answer and hide it when the call ends.
 */
class CallOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val telephony by lazy { getSystemService(TelephonyManager::class.java) }
    private var callback: Any? = null
    private var listening = false
    private var lastState = TelephonyManager.CALL_STATE_IDLE
    private var sessionId = 0
    private var sawActiveCall = false
    private val ringGrace = Runnable { if (!sawActiveCall) container.calls.onCallEnded(sessionId) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        listening = listenToCallState()
    }

    /** Called for every call session; the service may already be running for an earlier one. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sessionId = intent?.getIntExtra(EXTRA_SESSION, 0) ?: 0
        // A call waiting during another call never goes through RINGING from IDLE.
        sawActiveCall = lastState != TelephonyManager.CALL_STATE_IDLE
        handler.removeCallbacks(ringGrace)
        // The caller may hang up between screening and this point; then the phone never rings.
        if (listening) handler.postDelayed(ringGrace, RING_GRACE_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterCallState()
        super.onDestroy()
    }

    private fun onCallState(state: Int) {
        lastState = state
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> sawActiveCall = true
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                sawActiveCall = true
                container.calls.onCallAnswered(sessionId)
            }
            // Screening runs before the phone starts ringing, so an initial IDLE must be ignored.
            TelephonyManager.CALL_STATE_IDLE -> if (sawActiveCall) container.calls.onCallEnded(sessionId)
        }
    }

    private fun listenToCallState(): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onCallState(state)
            }
            telephony.registerTelephonyCallback(mainExecutor, cb)
            callback = cb
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onCallState(state)
            }
            @Suppress("DEPRECATION")
            telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            callback = listener
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun unregisterCallState() {
        val cb = callback ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephony.unregisterTelephonyCallback(cb as TelephonyCallback)
        } else {
            telephony.listen(cb as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
        callback = null
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Identyfikacja połączeń", NotificationManager.IMPORTANCE_MIN)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sprawdzam numer…")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "call_identification"
        private const val NOTIFICATION_ID = 1
        private const val RING_GRACE_MS = 10_000L
        private const val EXTRA_SESSION = "session"

        fun start(context: Context, sessionId: Int) {
            context.startForegroundService(Intent(context, CallOverlayService::class.java).putExtra(EXTRA_SESSION, sessionId))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallOverlayService::class.java))
        }
    }
}
