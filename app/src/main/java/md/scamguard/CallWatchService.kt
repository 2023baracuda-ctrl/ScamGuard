package md.scamguard

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

/**
 * Пассивное наблюдение за звонком — НЕ скринер, ничего не блокирует,
 * только запоминает в CallContext состояние и номер. Нужно только READ_PHONE_STATE.
 */
class CallWatchService : Service() {

    private val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            CallContext.markState(state)   // RINGING / OFFHOOK / IDLE
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try { startAsForeground() } catch (t: Throwable) {
            android.util.Log.w("ScamGuard", "fg start failed: " + t.message)
        }
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) tm.registerTelephonyCallback(mainExecutor, cb)
        } catch (t: Throwable) {
            android.util.Log.w("ScamGuard", "telephony cb failed: " + t.message)
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 31)
            getSystemService(TelephonyManager::class.java).unregisterTelephonyCallback(cb)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val ch = "watch"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(ch) == null)
            nm.createNotificationChannel(NotificationChannel(ch, "Защита активна",
                NotificationManager.IMPORTANCE_MIN))
        val n = NotificationCompat.Builder(this, ch)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("ScamGuard")
            .setContentText("Защита включена")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 30)
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(7, n)
    }

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, CallWatchService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
