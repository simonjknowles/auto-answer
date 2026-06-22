package com.simon.autoanswer.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.simon.autoanswer.audio.AudioRouter
import com.simon.autoanswer.data.Prefs

class CallAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != PKG_WHATSAPP && pkg != PKG_WHATSAPP_BUSINESS) return

        val prefs = Prefs.get(this)
        if (!prefs.enabled.value) return
        if (prefs.testMode.value) return

        if (!AnswerState.consumeIfFresh()) return

        Log.i(TAG, "Call screen visible + armed; scheduling tap in ${prefs.delayMs.value} ms")
        handler.postDelayed({ tryAnswer() }, prefs.delayMs.value.toLong())
    }

    override fun onInterrupt() {}

    private fun tryAnswer() {
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "No active window")
            return
        }
        if (Prefs.get(this).forceBluetoothAudio.value) {
            AudioRouter.routeToBluetoothIfAvailable(this)
        }
        val target = findAnswerNode(root)
        if (target != null) {
            val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "Tapped Answer node ok=$ok id=${target.viewIdResourceName}")
            if (!ok) swipeUpFallback(target)
            return
        }
        Log.w(TAG, "No Answer node found, trying swipe-up fallback")
        swipeUpFallback(null)
    }

    private fun findAnswerNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = ID_HINTS.firstNotNullOfOrNull { hint ->
            root.findAccessibilityNodeInfosByViewId("$PKG_WHATSAPP:id/$hint").firstOrNull()
                ?: root.findAccessibilityNodeInfosByViewId("$PKG_WHATSAPP_BUSINESS:id/$hint").firstOrNull()
        }
        if (byId != null) return byId

        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val matchesText = TEXT_HINTS.any { hint ->
                n.text?.toString()?.contains(hint, ignoreCase = true) == true ||
                    n.contentDescription?.toString()?.contains(hint, ignoreCase = true) == true
            }
            if (matchesText && n.isClickable) return n
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun swipeUpFallback(targetHint: AccessibilityNodeInfo?) {
        val bounds = Rect()
        if (targetHint != null) {
            targetHint.getBoundsInScreen(bounds)
        } else {
            val root = rootInActiveWindow ?: return
            root.getBoundsInScreen(bounds)
        }
        if (bounds.isEmpty) {
            Log.w(TAG, "Empty bounds, cannot dispatch swipe")
            return
        }
        val cx = bounds.exactCenterX()
        val startY = bounds.exactCenterY() + bounds.height() * 0.2f
        val endY = bounds.exactCenterY() - bounds.height() * 0.6f

        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 350)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGesture(gesture, null, null)
        Log.i(TAG, "Swipe-up fallback dispatched ok=$ok ($cx, $startY -> $cx, $endY)")
    }

    companion object {
        private const val TAG = "CallA11yService"
        private const val PKG_WHATSAPP = "com.whatsapp"
        private const val PKG_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

        private val ID_HINTS = listOf(
            "accept_call_btn",
            "accept_incoming_call_view",
            "call_accept_btn",
            "btn_accept",
            "answer_call_btn",
        )

        private val TEXT_HINTS = listOf("answer", "accept")
    }
}
