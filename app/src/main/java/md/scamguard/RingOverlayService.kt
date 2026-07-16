package md.scamguard

import android.net.Uri
import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon

/**
 * Оверлей поверх экрана входящего звонка (SYSTEM_ALERT_WINDOW), а не отдельная
 * Activity — так экран набора номера/ответа остаётся виден и рабочим,
 * баннер занимает только верхнюю часть экрана.
 */
class RingOverlayService : Service() {

    private var overlayView: View? = null
    private var wm: WindowManager? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        val minutesAgo = intent?.getIntExtra(EX_MINUTES, 0) ?: 0
        val categoryName = intent?.getStringExtra(EX_CATEGORY) ?: ReasonCategory.OTHER.name
        val bankName = intent?.getStringExtra(EX_BANK_NAME) ?: ""
        val bankCategory = intent?.getStringExtra(EX_BANK_CATEGORY) ?: ""
        val bankPhone = intent?.getStringExtra(EX_BANK_PHONE) ?: ""

        showOverlay(minutesAgo, categoryName, bankName, bankCategory, bankPhone)
        startVibration()
        return START_NOT_STICKY
    }

    @SuppressLint("InflateParams")
    @Suppress("CyclomaticComplexMethod")
    private fun showOverlay(minutesAgo: Int, categoryName: String, bankName: String, bankCategory: String, bankPhone: String) {
        removeOverlay()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val lang = LocaleHelper.readSavedLang(this)
        val lctx = LocaleHelper.wrap(this, lang)
        fun s(id: Int, vararg args: Any) = lctx.resources.getString(id, *args)

        val category = runCatching { ReasonCategory.valueOf(categoryName) }.getOrDefault(ReasonCategory.OTHER)
        val subjectRaw = if (lang == "ro") category.ro else category.ru
        val subjectCap = subjectRaw.replaceFirstChar { it.uppercase() }
        val subjectLine = if (bankName.isNotBlank())
            s(R.string.ring_alert_subject_from, subjectCap, bankName) else subjectCap

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#D9CA8A04"))
                cornerRadius = dp(18).toFloat()
            }
        }

        root.addView(TextView(this).apply {
            text = s(R.string.ring_alert_title)
            setTextColor(Color.WHITE); textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = s(R.string.ring_alert_time_ago, minutesAgo)
            setTextColor(Color.WHITE); textSize = 14f
            setPadding(0, dp(6), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = subjectLine
            setTextColor(Color.WHITE); textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(14))
        })

        fun roundedBg(colorHex: String) = GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            cornerRadius = dp(12).toFloat()
        }

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val hangupBtn = Button(this).apply {
            text = s(R.string.ring_alert_btn_hangup)
            setTextColor(Color.BLACK)
            background = roundedBg("#F5A3A3")
            textSize = 12f
            setOnClickListener { endActiveCall(); removeOverlay(); stopSelf() }
        }
        btnRow.addView(hangupBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

        if (bankPhone.isNotBlank()) {
            val callBtn = Button(this).apply {
                text = "📞 " + s(R.string.ring_alert_btn_call_official, bankName.ifBlank { bankPhone })
                setTextColor(Color.BLACK)
                background = roundedBg("#A7F3D0")
                textSize = 12f
                setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$bankPhone"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    removeOverlay(); stopSelf()
                }
            }
            btnRow.addView(callBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val closeBtn = Button(this).apply {
            text = s(R.string.ring_alert_btn_understood)
            setTextColor(Color.BLACK)
            background = roundedBg("#FFFFFF")
            setOnClickListener { removeOverlay(); stopSelf() }
        }
        root.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        overlayView = root
        runCatching { wm?.addView(root, params) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun removeOverlay() {
        overlayView?.let { v -> runCatching { wm?.removeView(v) } }
        overlayView = null
        stopVibration()
    }

    @SuppressLint("MissingPermission")
    private fun endActiveCall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        try {
            (getSystemService(TelecomManager::class.java)).endCall()
        } catch (_: Exception) {}
    }

    private fun startVibration() {
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else { @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator }
        if (!v.hasVibrator()) return
        vibrator = v
        val pattern = longArrayOf(0, 400, 800)
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                val attrs = android.os.VibrationAttributes.Builder()
                    .setUsage(android.os.VibrationAttributes.USAGE_ALARM).build()
                v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
            } else if (Build.VERSION.SDK_INT >= 26) {
                val attrs = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
            } else {
                @Suppress("DEPRECATION") v.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopVibration() {
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_DISMISS = "md.scamguard.RING_DISMISS"
        private const val EX_MINUTES = "min"
        private const val EX_CATEGORY = "cat"
        private const val EX_BANK_NAME = "bank_name"
        private const val EX_BANK_PHONE = "bank_phone"
        private const val EX_BANK_CATEGORY = "bank_cat"

        fun show(ctx: Context, minutesAgo: Int, category: ReasonCategory, bank: BankMatch?) {
            if (!Settings.canDrawOverlays(ctx)) return
            val i = Intent(ctx, RingOverlayService::class.java).apply {
                putExtra(EX_MINUTES, minutesAgo)
                putExtra(EX_CATEGORY, category.name)
                putExtra(EX_BANK_NAME, bank?.displayName ?: "")
                putExtra(EX_BANK_CATEGORY, bank?.category ?: "")
                putExtra(EX_BANK_PHONE, bank?.phone ?: "")
            }
            runCatching { ctx.startService(i) }
        }

        fun dismiss(ctx: Context) {
            runCatching {
                ctx.startService(Intent(ctx, RingOverlayService::class.java).apply {
                    action = ACTION_DISMISS
                })
            }
        }
    }
}
