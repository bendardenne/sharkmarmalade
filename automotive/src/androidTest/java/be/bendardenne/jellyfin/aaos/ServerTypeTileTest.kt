package be.bendardenne.jellyfin.aaos

import android.os.Parcelable
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerTypeTileTest {
    @Test
    fun formFitsPortraitAndLandscapeAndScrollsInShortWindows() = onMainThread {
        for ((widthDp, heightDp) in listOf(800 to 480, 360 to 800, 640 to 240)) {
            val screen = inflateScreen() as ScrollView
            val density = screen.resources.displayMetrics.density
            fun dp(value: Int) = (value * density + 0.5f).toInt()
            val width = dp(widthDp)
            val height = dp(heightDp)
            screen.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            screen.layout(0, 0, width, height)

            val form = screen.findViewById<View>(R.id.server_sign_in_form)
            val logo = screen.findViewById<View>(R.id.server_sign_in_logo)
            val input = screen.findViewById<View>(R.id.server_uri)
            val tiles = screen.findViewById<View>(R.id.server_type_group)
            val submit = screen.findViewById<View>(R.id.submit_server_button)
            val progress = screen.findViewById<View>(R.id.progress_bar)
            assertTrue(form.width <= dp(480))
            assertTrue(form.left >= dp(24))
            assertTrue(form.right <= width - dp(24))
            assertEquals(dp(96), logo.width)
            assertEquals(dp(68), logo.height)
            assertTrue(logo.bottom < input.top)
            assertTrue(input.bottom < tiles.top)
            assertTrue(tiles.bottom < submit.top)
            assertTrue(tiles.left >= 0)
            assertTrue(tiles.right <= form.width)
            assertTrue(progress.right <= input.right)
            assertTrue(submit.height >= dp(64))
            if (heightDp == 240) {
                assertTrue(screen.getChildAt(0).height > height)
                screen.fullScroll(View.FOCUS_DOWN)
                assertTrue(screen.scrollY > 0)
            } else {
                assertTrue(form.top + submit.bottom <= height - dp(24))
            }
        }
    }

    @Test
    fun jellyfinIsSelectedInitiallyAndTilesAreSquare() = onMainThread {
        val view = inflateScreen()
        val group = view.findViewById<RadioGroup>(R.id.server_type_group)
        assertEquals(R.id.radio_jellyfin, group.checkedRadioButtonId)
        assertTrue(view.findViewById<RadioButton>(R.id.radio_jellyfin).isChecked)
        assertFalse(view.findViewById<RadioButton>(R.id.radio_subsonic).isChecked)

        for (id in listOf(R.id.radio_jellyfin, R.id.radio_subsonic)) {
            val tile = view.findViewById<RadioButton>(id)
            val expectedSize = (120 * tile.resources.displayMetrics.density + 0.5f).toInt()
            assertEquals(expectedSize, tile.layoutParams.width)
            assertEquals(expectedSize, tile.layoutParams.height)
            assertNull(tile.buttonDrawable)
            assertNotNull(tile.compoundDrawables[1])
            assertTrue(tile.text.isNotEmpty())
        }
    }

    @Test
    fun switchingTilesKeepsExactlyOneServerSelected() = onMainThread {
        val view = inflateScreen()
        val group = view.findViewById<RadioGroup>(R.id.server_type_group)
        val jellyfin = view.findViewById<RadioButton>(R.id.radio_jellyfin)
        val subsonic = view.findViewById<RadioButton>(R.id.radio_subsonic)

        subsonic.performClick()
        assertEquals(R.id.radio_subsonic, group.checkedRadioButtonId)
        assertFalse(jellyfin.isChecked)
        assertTrue(subsonic.isChecked)

        subsonic.performClick()
        assertEquals(R.id.radio_subsonic, group.checkedRadioButtonId)
        assertFalse(jellyfin.isChecked)
        assertTrue(subsonic.isChecked)

        jellyfin.performClick()
        assertEquals(R.id.radio_jellyfin, group.checkedRadioButtonId)
        assertTrue(jellyfin.isChecked)
        assertFalse(subsonic.isChecked)
    }

    @Test
    fun restoredSelectionOverridesTheInitialSelection() = onMainThread {
        val original = inflateScreen()
        original.findViewById<RadioButton>(R.id.radio_subsonic).performClick()
        val state = SparseArray<Parcelable>()
        original.saveHierarchyState(state)

        val restored = inflateScreen()
        restored.restoreHierarchyState(state)
        assertEquals(
            R.id.radio_subsonic,
            restored.findViewById<RadioGroup>(R.id.server_type_group).checkedRadioButtonId
        )
        assertFalse(restored.findViewById<RadioButton>(R.id.radio_jellyfin).isChecked)
        assertTrue(restored.findViewById<RadioButton>(R.id.radio_subsonic).isChecked)
    }

    private fun inflateScreen(): View {
        val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.SharkMarmalade
        )
        return LayoutInflater.from(context).inflate(R.layout.server_sign_in, null)
    }

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
