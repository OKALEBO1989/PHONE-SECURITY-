package com.okaleb.phoneguard

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class PowerGuardService : AccessibilityService() {

    private val words = listOf(
        "power off", "switch off", "shut down", "shutdown",
        "restart", "reboot", "turn off"
    )
    private var last = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AlarmService.running || event == null) return
        if (event.packageName?.toString() == packageName) return
        val root = rootInActiveWindow ?: return
        if (hasPowerText(root)) {
            val now = System.currentTimeMillis()
            if (now - last > 500) {
                last = now
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun hasPowerText(n: AccessibilityNodeInfo?): Boolean {
        if (n == null) return false
        val t = (n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")
        val low = t.lowercase()
        if (words.any { low.contains(it) }) return true
        for (i in 0 until n.childCount) {
            if (hasPowerText(n.getChild(i))) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
