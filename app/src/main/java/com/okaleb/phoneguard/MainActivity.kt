package com.okaleb.phoneguard

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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

    private fun edit(hintText: String, value: String, type: Int) =
        EditText(this).apply {
            setHint(hintText)
            setText(value)
            inputType = type
        }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("pg", MODE_PRIVATE)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = "PHONE SECURITY"
            textSize = 24f
        }
        val email = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val pw = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val pinType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

        val to = edit("Email to receive alerts", prefs.getString("to", "") ?: "", email)
        val gmail = edit("Gmail address that sends alerts", prefs.getString("gmail", "") ?: "", email)
        val app = edit("Gmail app password", prefs.getString("apppass", "") ?: "", pw)
        val pin = edit("Security PIN", prefs.getString("pin", "") ?: "", pinType)

        root.addView(title)
        listOf(to, gmail, app, pin).forEach { root.addView(it) }

        root.addView(button("Save settings") {
            prefs.edit()
                .putString("to", to.text.toString().trim())
                .putString("gmail", gmail.text.toString().trim())
                .putString("apppass", app.text.toString().trim())
                .putString("pin", pin.text.toString().trim())
                .apply()
            toast("Saved")
        })

        root.addView(button("Test email") {
            Mailer.send(this, "PHONE SECURITY TEST", "Test email from Phone Security.") { r ->
                runOnUiThread { toast(r) }
            }
        })

        root.addView(button("Enable theft detection (device admin)") {
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

        root.addView(button("Test alarm") {
            if ((prefs.getString("pin", "") ?: "").isBlank()) {
                toast("Save a PIN first")
            } else {
                ContextCompat.startForegroundService(
                    this, Intent(this, AlarmService::class.java)
                )
            }
        })

        val stopPin = edit("Enter PIN to stop alarm", "", pinType)
               root.addView(button("Enable power-off block (accessibility)") {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(stopPin)
        root.addView(button("Stop alarm") {
            if (stopPin.text.toString() == prefs.getString("pin", "")) {
                stopService(Intent(this, AlarmService::class.java))
                stopPin.setText("")
                toast("Alarm stopped")
            } else toast("Wrong PIN")
        })

        setContentView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(root)
        })

        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        basicPerms.launch(perms.toTypedArray())
    }
}
