package be.bendardenne.jellyfin.aaos

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsernamePasswordLayoutTest {
    @Test
    fun narrowScreensStackTheFormAndScrollWhenShort() = onMainThread {
        for ((widthDp, heightDp) in listOf(360 to 800, 800 to 480, 700 to 300)) {
            val screen = layoutFor(widthDp, heightDp)
            val dp = dpConverter(screen)
            val form = screen.findViewById<View>(R.id.form)
            val logo = screen.findViewById<View>(R.id.server_sign_in_logo)
            val quickConnect = screen.findViewById<View>(R.id.quickconnect_layout)
            val divider = screen.findViewById<View>(R.id.divider)
            val username = screen.findViewById<View>(R.id.username)
            val login = screen.findViewById<View>(R.id.login_button)

            assertTrue(form.width <= dp(480))
            assertTrue(form.left >= dp(24) && form.right <= dp(widthDp) - dp(24))
            assertEquals(dp(96), logo.width)
            assertEquals(dp(68), logo.height)
            assertTrue(logo.bottom <= quickConnect.top + form.top)
            assertTrue(quickConnect.bottom < divider.top)
            assertTrue(divider.bottom < username.top + (username.parent as View).top)
            assertTrue(login.height >= dp(64))
            assertTrue(login.right <= (login.parent as View).width)
            assertScrollsOnlyWhenNeeded(screen, heightDp)
        }
    }

    @Test
    fun wideScreensPlaceQuickConnectBesideTheCredentials() = onMainThread {
        val screen = layoutFor(1170, 700)
        val dp = dpConverter(screen)
        val form = screen.findViewById<View>(R.id.form)
        val logo = screen.findViewById<View>(R.id.server_sign_in_logo)
        val quickConnect = screen.findViewById<View>(R.id.quickconnect_layout)
        val username = screen.findViewById<View>(R.id.username)
        val login = screen.findViewById<View>(R.id.login_button)

        assertTrue(form.left >= dp(24) && form.right <= dp(1170) - dp(24))
        assertTrue(logo.bottom <= quickConnect.top + (quickConnect.parent as View).top)
        assertTrue(quickConnect.right <= (username.parent as View).left + (quickConnect.parent as View).left)
        assertEquals(dp(400), username.width)
        assertTrue(login.height >= dp(64))
        assertScrollsOnlyWhenNeeded(screen, 700)
    }

    @Test
    fun subsonicLayoutStillFitsWhenQuickConnectIsHidden() = onMainThread {
        for ((widthDp, heightDp) in listOf(360 to 800, 1170 to 700)) {
            val screen = layoutFor(widthDp, heightDp, hideQuickConnect = true)
            val dp = dpConverter(screen)
            val form = screen.findViewById<View>(R.id.form)
            val username = screen.findViewById<View>(R.id.username)
            val login = screen.findViewById<View>(R.id.login_button)

            assertTrue(form.left >= dp(24) && form.right <= dp(widthDp) - dp(24))
            assertTrue(username.width in 1..dp(480))
            assertTrue(login.height >= dp(64))
        }
    }

    private fun assertScrollsOnlyWhenNeeded(screen: ScrollView, heightDp: Int) {
        val height = dpConverter(screen)(heightDp)
        val contentHeight = screen.getChildAt(0).height
        if (contentHeight > height) {
            screen.scrollTo(0, contentHeight - height)
            assertEquals(contentHeight - height, screen.scrollY)
        } else {
            assertEquals(0, screen.scrollY)
        }
    }

    private fun layoutFor(widthDp: Int, heightDp: Int, hideQuickConnect: Boolean = false): ScrollView {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            smallestScreenWidthDp = minOf(widthDp, heightDp)
        }
        val context = ContextThemeWrapper(base.createConfigurationContext(configuration), R.style.SharkMarmalade)
        val screen = LayoutInflater.from(context).inflate(R.layout.username_password_sign_in, null) as ScrollView
        if (hideQuickConnect) {
            screen.findViewById<View>(R.id.quickconnect_layout).visibility = View.GONE
            screen.findViewById<View>(R.id.divider).visibility = View.GONE
        }
        val dp = dpConverter(screen)
        val width = dp(widthDp)
        val height = dp(heightDp)
        screen.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        screen.layout(0, 0, width, height)
        return screen
    }

    private fun dpConverter(view: View): (Int) -> Int {
        val density = view.resources.displayMetrics.density
        return { value -> (value * density + 0.5f).toInt() }
    }

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
