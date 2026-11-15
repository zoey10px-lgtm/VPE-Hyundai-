package com.vpe.hyundai

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import java.io.File

/** Runs on a real Android emulator without a third-party test framework. */
class UiRegressionInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val result = Bundle()
        var activity: Activity? = null
        try {
            activity = startActivitySync(Intent().setClassName(targetContext, "com.vpe.hyundai.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val screen = activity
            fun call(name: String, vararg args: String) = runOnMainSync {
                val method = screen.javaClass.getDeclaredMethod(name, *Array(args.size) { String::class.java })
                method.isAccessible = true
                method.invoke(screen, *args)
            }
            fun scroll(): ScrollView {
                val field = screen.javaClass.getDeclaredField("pageScroll").apply { isAccessible = true }
                return field.get(screen) as ScrollView
            }
            fun settle() { waitForIdleSync(); SystemClock.sleep(900); waitForIdleSync() }
            fun screenshot(name: String) {
                val file = File(targetContext.getExternalFilesDir(null), name)
                val bitmap = uiAutomation.takeScreenshot() ?: error("No screenshot")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            call("loadDemo")
            settle()
            screenshot("ui-report-top.png")
            fun countGauges(view: View): Int = (if (view is ProgressBar) 1 else 0) +
                (if (view is ViewGroup) (0 until view.childCount).sumOf { countGauges(view.getChildAt(it)) } else 0)
            check(countGauges(scroll()) >= 11) { "Report gauges missing" }
            runOnMainSync { scroll().scrollTo(0, 600) }
            settle()
            val before = scroll().scrollY
            check(before > 0) { "Report did not scroll" }
            repeat(3) {
                call("render", "report")
                settle()
                check(scroll().scrollY == before) { "Live refresh reset report scroll: $before -> ${scroll().scrollY}" }
            }
            screenshot("ui-report-gauges.png")
            // A refresh received during a drag must not detach the touched view tree.
            val heldScroll = scroll()
            val point = IntArray(2)
            runOnMainSync { heldScroll.getLocationOnScreen(point) }
            val downTime = SystemClock.uptimeMillis()
            val x = (point[0] + heldScroll.width / 2).toFloat()
            val y = (point[1] + heldScroll.height / 2).toFloat()
            sendPointerSync(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
            call("render", "report")
            check(scroll() === heldScroll) { "Refresh interrupted active touch" }
            sendPointerSync(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
            settle()
            check(scroll().scrollY == before) { "Deferred refresh lost scroll" }
            call("render", "home")
            settle()
            runOnMainSync { scroll().scrollTo(0, 500) }
            settle()
            val homePosition = scroll().scrollY
            call("render", "home")
            settle()
            check(scroll().scrollY == homePosition) { "Home refresh reset scroll" }
            screenshot("ui-home-cards.png")
            call("render", "report")
            settle()
            check(scroll().scrollY == before) { "Tab navigation lost report position" }
            result.putString("stream", "UI_REGRESSION_PASS: report/home refresh, drag deferral, tab positions, gauges\n")
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "UI_REGRESSION_FAIL: ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
