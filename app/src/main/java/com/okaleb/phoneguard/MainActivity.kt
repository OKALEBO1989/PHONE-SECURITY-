package com.okaleb.phoneguard

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences

    // ---- Colors ----
    private val cBg = Color.parseColor("#F1F5F7")
    private val cCard = Color.WHITE
    private val cBorder = Color.parseColor("#D5DEE3")
    private val cPrimary = Color.parseColor("#0F766E")
    private val cPrimaryDark = Color.parseColor("#0B3B4A")
    private val cTonal = Color.parseColor("#DDF1EE")
    private val cDanger = Color.parseColor("#DC2626")
    private val cText = Color.parseColor("#1F2933")
    private val cMuted = Color.parseColor("#6B7A86")

    private val bgLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val basicPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            askBackground()
        }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askBackground() {
        if (Build.VERSION.SDK_INT >= 29 &&
            granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) bgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    // ---- Style helpers ----
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun shape(fill: Int, stroke: Int? = null, radius: Int = 14) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun lp(top: Int = 0) =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }

    private fun card(title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(cCard, cBorder, 18)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = lp(14)
            addView(TextView(context).apply {
                text = title.uppercase()
                textSize = 12f
                letterSpacing = 0.08f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(cPrimary)
            })
        }

    private fun label(t: String) =
        TextView(this).apply {
            text = t
            textSize = 12f
            setTextColor(cMuted)
            layoutParams = lp(14)
        }

    private fun edit(hintText: String, value: String, type: Int) =
        EditText(this).apply {
            setHint(hintText)
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setText(value)
            inputType = type
            textSize = 16f
            setTextColor(cText)
            background = shape(Color.parseColor("#FAFCFD"), cBorder, 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = lp(4)
        }

    private fun field(parent: LinearLayout, labelText: String, et: EditText) {
        parent.addView(label(labelText))
        parent.addView(et)
    }

    private enum class Style { PRIMARY, TONAL, OUTLINE, DANGER }

    private fun button(label: String, style: Style = Style.OUTLINE, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            minHeight = dp(50)
            stateListAnimator = null
            elevation = 0f
            val fill: Int
            val stroke: Int?
            when (style) {
                Style.PRIMARY -> { fill = cPrimary; stroke = null; setTextColor(Color.WHITE) }
                Style.TONAL -> { fill = cTonal; stroke = null; setTextColor(cPrimaryDark) }
                Style.OUTLINE -> { fill = cCard; stroke = cPrimary; setTextColor(cPrimary) }
                Style.DANGER -> { fill = cDanger; stroke = null; setTextColor(Color.WHITE) }
            }
            background = RippleDrawable(
                ColorStateList.valueOf(Color.parseColor("#26000000")),
                shape(fill, stroke, 14), null
            )
            layoutParams = lp(12)
            setOnClickListener { action() }
        }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pg", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // Header banner
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(cPrimaryDark, cPrimary)
            ).apply { cornerRadius = dp(20).toFloat() }
            setPadding(dp(20), dp(22), dp(20), dp(22))
            addView(TextView(context).apply {
                text = "Phone Security"
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "Alerts, photo capture and alarm protection"
                textSize = 13f
                setTextColor(Color.parseColor("#CFE9E6"))
                layoutParams = lp(4)
            })
                    protect.addView(button("Enable remote alarm (email)", Style.TONAL) {
            if ((prefs.getString("pin", "") ?: "").isBlank()) {
                toast("Save settings and a PIN first")
            } else {
                ContextCompat.startForegroundService(
                    this, Intent(this, RemoteCommandService::class.java)
                )
                toast("Remote alarm is listening")
            }
        })
        protect.addView(button("Disable remote alarm") {
            if (stopPin.text.toString() == prefs.getString("pin", "")) {
                stopService(Intent(this, RemoteCommandService::class.java))
                stopPin.setText("")
                toast("Remote alarm disabled")
            } else toast("Enter your PIN in the alarm card first")
        })
        }
        root.addView(header)

        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val pinType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val to = edit("Email to receive alerts", prefs.getString("to", "") ?: "", email)
        val gmail = edit("Gmail address that sends alerts", prefs.getString("gmail", "") ?: "", email)
        val app = edit("Gmail app password", prefs.getString("apppass", "") ?: "", pw)
        val pin = edit("Security PIN", prefs.getString("pin", "") ?: "", pinType)
        val stopPin = edit("Enter PIN", "", pinType)

        // ---- Account ----
        val account = card("Account")
        field(account, "Alert recipient", to)
        field(account, "Sender Gmail", gmail)
        field(account, "App password", app)
        field(account, "Security PIN", pin)
        account.addView(button("Save settings", Style.PRIMARY) {
            prefs.edit()
                .putString("to", to.text.toString().trim())
                .putString("gmail", gmail.text.toString().trim())
                .putString("apppass", app.text.toString().trim())
                .putString("pin", pin.text.toString().trim())
                .apply()
            toast("Saved")
        })
        root.addView(account)

        // ---- Tests ----
        val tests = card("Test your setup")
        tests.addView(button("Send test email") {
            Mailer.send(this, "PHONE SECURITY TEST", "Test email from Phone Security.") { r ->
                runOnUiThread { toast(r) }
            }
        })
        tests.addView(button("Take test photo") {
            if (!granted(Manifest.permission.CAMERA)) {
                toast("Allow the Camera permission first")
            } else {
                ContextCompat.startForegroundService(
                    this, Intent(this, PhotoService::class.java)
                )
                toast("Taking photo. Check your email in a minute.")
            }
        })
        root.addView(tests)

        // ---- Protection ----
        val protect = card("Protection")
        protect.addView(button("Enable power-off block (accessibility)", Style.TONAL) {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        protect.addView(button("Enable theft detection (device admin)", Style.TONAL) {
            val cn = ComponentName(this, PhoneGuardAdmin::class.java)
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Needed to detect wrong unlock attempts."
                    )
            )
        })
        protect.addView(button("Arm owner guard", Style.PRIMARY) {
            if ((prefs.getString("pin", "") ?: "").isBlank()) {
                toast("Save a PIN first")
            } else {
                ContextCompat.startForegroundService(
                    this, Intent(this, GuardService::class.java)
                )
                toast("Guard armed. Lock the phone and leave it.")
            }
        })
        root.addView(protect)

        // ---- Alarm & disarm ----
        val alarm = card("Alarm and disarm")
        alarm.addView(button("Test alarm") {
            if ((prefs.getString("pin", "") ?: "").isBlank()) {
                toast("Save a PIN first")
            } else {
                ContextCompat.startForegroundService(
                    this, Intent(this, AlarmService::class.java)
                )
            }
        })
        field(alarm, "PIN to stop alarm or disarm guard", stopPin)
        alarm.addView(button("Stop alarm", Style.DANGER) {
            if (stopPin.text.toString() == prefs.getString("pin", "")) {
                stopService(Intent(this, AlarmService::class.java))
                stopPin.setText("")
                toast("Alarm stopped")
            } else toast("Wrong PIN")
        })
        alarm.addView(button("Disarm owner guard") {
            if (stopPin.text.toString() == prefs.getString("pin", "")) {
                stopService(Intent(this, GuardService::class.java))
                stopPin.setText("")
                toast("Guard disarmed")
            } else toast("Wrong PIN")
        })
        root.addView(alarm)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(cBg)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(root)
        })

        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        basicPerms.launch(perms.toTypedArray())
    }
}
