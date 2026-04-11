package com.axion.tempus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class NotificationShadeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        clearActiveService()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clearActiveService()
        return super.onUnbind(intent)
    }

    private fun clearActiveService() {
        if (activeService === this) {
            activeService = null
        }
    }

    companion object {
        @Volatile
        private var activeService: NotificationShadeAccessibilityService? = null

        fun isEnabled(): Boolean {
            return activeService != null
        }

        fun expandNotificationsPanel(): Boolean {
            return activeService?.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) == true
        }
    }
}
