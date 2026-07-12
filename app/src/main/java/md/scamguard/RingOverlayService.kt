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

        showOverlay(minutesAgo, categoryName, bankName, bankCategory)
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
        val subjectText = if (lang == "ro") category.ro else category.ru
        val activityText = BankCategoryLabels.get(lctx, bankCategory)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CA8A04"))
                cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
            }
        }

        fun row(label: String, value: String) {
            if (value.isBlank()) return
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(TextView(this).apply {
                text = label; setTextColor(Color.parseColor("#FFE4E6")); textSize = 13f
                minWidth = dp(110)
            })
            r.addView(TextView(this).apply {
                text = value; setTextColor(Color.WHITE); textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            root.addView(r, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
        }

        root.addView(TextView(this).apply {
            text = s(R.string.ring_alert_title)
            setTextColor(Color.WHITE); textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = s(R.string.ring_alert_time_ago, minutesAgo)
            setTextColor(Color.WHITE); textSize = 14f
            setPadding(0, dp(6), 0, dp(8))
        })

        row(s(R.string.ring_alert_sender), bankName.ifBlank { "—" })
        row(s(R.string.ring_alert_category), activityText)
        row(s(R.string.ring_alert_subject), subjectText)

        root.addView(TextView(this).apply {
            text = s(R.string.ring_alert_no_share)
            setTextColor(Color.parseColor("#FFE4E6")); textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(12))
        })

       val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hangupBtn = Button(this).apply {
            text = s(R.string.ring_alert_btn_hangup)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1D4ED8"))
            setOnClickListener { endActiveCall(); removeOverlay(); stopSelf() }
        }
        val closeBtn = Button(this).apply {
            text = s(R.string.ring_alert_btn_understood)
            setTextColor(Color.parseColor("#FCD5CE"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { removeOverlay(); stopSelf() }
        }
        btnRow.addView(hangupBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        btnRow.addView(closeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })

        if (bankPhone.isNotBlank()) {
            val callBtn = Button(this).apply {
                text = s(R.string.ring_alert_btn_call_official, bankPhone)
                setTextColor(Color.parseColor("#FFE4E6"))
                setBackgroundColor(Color.TRANSPARENT)
                textSize = 12f
                setOnClickListener {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$bankPhone"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
            root.addView(callBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) })
        }

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
            gravity = Gravity.TOP
            y = dp(24)
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
