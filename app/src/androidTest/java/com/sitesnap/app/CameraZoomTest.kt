package com.sitesnap.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real camera screen with real touch gestures: a two-finger pinch on the viewfinder and the
 * zoom slider. Needs a device or emulator with a camera that can zoom (the emulator's front camera can).
 *
 *     ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CameraZoomTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pkg = "com.sitesnap.app"

    @Before
    fun openCamera() {
        device.executeShellCommand("pm grant $pkg android.permission.CAMERA")
        device.executeShellCommand("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
        device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 10_000)

        find(By.text("Use offline for now"), 3_000)?.click()
        val existing = find(By.desc("Take photos"), 2_000) ?: find(By.text("Take photos"), 1_000)
        if (existing == null) {
            find(By.desc("New job"))!!.click()
            find(By.clazz("android.widget.EditText"))!!.text = "Zoom test"
            find(By.text("Create & start shooting"))!!.click()
        } else {
            existing.click()
        }
        find(By.desc("Switch camera"))!!
        // The emulator's back camera can't zoom; its front camera can.
        if (zoomChip(2_000) == null) {
            find(By.desc("Switch camera"))!!.click()
        }
        assertTrue("Camera shows no zoom controls", zoomChip(5_000) != null)
    }

    @Test
    fun twoFingerPinchZoomsIn() {
        val before = currentZoom()
        // Middle of the viewfinder, well above the zoom chips and shutter.
        val b = device.findObject(By.pkg(pkg).depth(0)).visibleBounds
        pinchOut(b.centerX(), (b.height() * 0.4).toInt())
        device.waitForIdle()
        Thread.sleep(500)
        val after = currentZoom()
        assertTrue("Pinch didn't zoom in ($before -> $after)", after > before + 0.5f)
    }

    @Test
    fun tappingActiveChipOpensSliderAndDraggingZooms() {
        val chip = zoomChip()!!
        val start = currentZoom()
        chip.click()
        // The slider (ruler dial) shows the live zoom value above it.
        val label = find(By.textEndsWith("×"), 3_000)!!
        val slider = label.visibleBounds
        device.swipe(slider.centerX() + 250, slider.bottom + 60, slider.centerX() - 250, slider.bottom + 60, 40)
        Thread.sleep(300)
        val after = currentZoom()
        assertTrue("Slider didn't zoom ($start -> $after)", after > start + 0.5f)
    }

    /** Two fingers moving apart vertically around [cx], [cy]. */
    private fun pinchOut(cx: Int, cy: Int) {
        val gap0 = 60
        val gap1 = 420
        val steps = 30
        val paths = Array(2) { arrayOfNulls<android.view.MotionEvent.PointerCoords>(steps + 1) }
        for (i in 0..steps) {
            val gap = gap0 + (gap1 - gap0) * i / steps
            paths[0][i] = android.view.MotionEvent.PointerCoords().apply { x = cx.toFloat(); y = (cy - gap).toFloat(); pressure = 1f; size = 1f }
            paths[1][i] = android.view.MotionEvent.PointerCoords().apply { x = cx.toFloat(); y = (cy + gap).toFloat(); pressure = 1f; size = 1f }
        }
        @Suppress("UNCHECKED_CAST")
        val ok = InteractionHelper.performTwoPointerGesture(device, paths[0] as Array<android.view.MotionEvent.PointerCoords>, paths[1] as Array<android.view.MotionEvent.PointerCoords>)
        assertTrue("Couldn't inject the pinch", ok)
    }

    private fun zoomChip(timeout: Long = 3_000): UiObject2? = find(By.textEndsWith("×"), timeout)

    private fun currentZoom(): Float {
        val text = zoomChip()?.text ?: error("No zoom value on screen")
        return text.removeSuffix("×").let { if (it.startsWith(".")) "0$it" else it }.toFloat()
    }

    private fun find(selector: BySelector, timeout: Long = 5_000): UiObject2? = device.wait(Until.findObject(selector), timeout)
}

/** Real two-finger gestures go through UiObject's multi-pointer injection. */
private object InteractionHelper {
    fun performTwoPointerGesture(
        device: UiDevice,
        first: Array<android.view.MotionEvent.PointerCoords>,
        second: Array<android.view.MotionEvent.PointerCoords>,
    ): Boolean {
        val uiObject = device.findObject(androidx.test.uiautomator.UiSelector().packageName("com.sitesnap.app"))
        return uiObject.performMultiPointerGesture(first, second)
    }
}
