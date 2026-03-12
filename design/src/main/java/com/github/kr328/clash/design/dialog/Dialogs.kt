package com.github.kr328.clash.design.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.github.kr328.clash.common.compat.isAllowForceDarkCompat
import com.github.kr328.clash.common.compat.isSystemBarsTranslucentCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.ui.Insets
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.resolveThemedResourceId
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class AppBottomSheetDialog(
    context: Context,
    private val forceExpanded: Boolean = true,
) : BottomSheetDialog(context) {
    private var insets: Insets = Insets.EMPTY

    /**
     * When set, UP/DOWN D-pad keys that hit the boundary of the dialog content are consumed
     * rather than escaping to the parent window. Should be set to the root view of the sheet
     * content (not just the RecyclerView) so that D-pad UP from the first list item can still
     * reach sibling buttons (e.g. sort / delay-test) above the RecyclerView.
     */
    var focusTrapView: View? = null

    /**
     * Called when the dialog window gains or loses system (input) focus.
     * If nothing inside the dialog is focused when window focus arrives, grab focus now.
     * This handles the timing gap between layout completion and the system granting
     * window focus — without this, D-pad events can still reach the background activity.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val trap = focusTrapView ?: return
        when (val focused = trap.findFocus()) {
            null -> trap.requestFocus()
            is RecyclerView -> if (focused.isFocused) focused.requestFocus(View.FOCUS_DOWN)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val trap = focusTrapView

        if (trap != null && event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        ) {
            // If a RecyclerView inside the trap has focus itself (not a child item), it is in
            // "scroll-only mode" — D-pad would scroll the list instead of navigating items.
            // Use directional requestFocus to break out of scroll-only mode and land on an item.
            val focused = trap.findFocus()
            if (focused is RecyclerView && focused.isFocused) {
                val dir = if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) View.FOCUS_DOWN else View.FOCUS_UP
                if (focused.requestFocus(dir)) return true
            }
            // Handle the rare case where the trap container itself has focus.
            if (trap.isFocused) {
                val dir = if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) View.FOCUS_DOWN else View.FOCUS_UP
                if (trap.requestFocus(dir)) return true
            }
        }

        val handled = super.dispatchKeyEvent(event)

        // If the event wasn't handled (at a dialog boundary), eat it to keep focus inside.
        // Only call requestFocus() when nothing inside the trap is focused yet; otherwise just
        // consume the key so focus stays at the boundary instead of jumping to item 0.
        if (!handled && event.action == KeyEvent.ACTION_DOWN && trap != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val currentFocus = trap.findFocus()
                    if (currentFocus == null || currentFocus === trap) {
                        trap.requestFocus()
                    }
                    return true
                }
            }
        }
        return handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setCancelable(true)

        window!!.apply {
            isSystemBarsTranslucentCompat = true
            isAllowForceDarkCompat = false
        }

        findViewById<ViewGroup>(com.google.android.material.R.id.container)?.apply {
            fitsSystemWindows = false
        }

        findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.apply {
            setOnInsertsChangedListener {
                if (insets != it) {
                    insets = it

                    (layoutParams as CoordinatorLayout.LayoutParams).also { params ->
                        if (ViewCompat.getLayoutDirection(this) == ViewCompat.LAYOUT_DIRECTION_LTR) {
                            params.setMargins(it.start, 0, it.end, 0)
                        } else {
                            params.setMargins(it.end, 0, it.start, 0)
                        }

                        val top = context.getPixels(R.dimen.bottom_sheet_background_padding_top)
                        val height = context.getPixels(R.dimen.bottom_sheet_header_height)

                        setPaddingRelative(
                            0,
                            top * 2 + height,
                            0,
                            it.bottom
                        )
                    }
                }
            }
        }

        setOnShowListener {
            if (forceExpanded) {
                behavior.skipCollapsed = true
                behavior.isHideable = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }
}

/**
 * A TV-optimized panel dialog for displaying proxy group content.
 * Extends Dialog directly (NOT BottomSheetDialog) so the window reliably
 * receives system D-pad focus on Android TV devices. BottomSheetDialog
 * is designed for touchscreens and on some TV platforms never obtains
 * input window focus, causing D-pad events to fall through to the activity.
 */
class TvProxyGroupDialog(context: Context) : Dialog(context) {

    var focusTrapView: View? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val trap = focusTrapView ?: return
        when (val focused = trap.findFocus()) {
            null -> trap.requestFocus()
            is RecyclerView -> if (focused.isFocused) focused.requestFocus(View.FOCUS_DOWN)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val trap = focusTrapView
        if (trap != null && event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_UP || event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
        ) {
            val focused = trap.findFocus()
            if (focused is RecyclerView && focused.isFocused) {
                val dir = if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) View.FOCUS_DOWN else View.FOCUS_UP
                if (focused.requestFocus(dir)) return true
            }
            if (trap.isFocused) {
                val dir = if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) View.FOCUS_DOWN else View.FOCUS_UP
                if (trap.requestFocus(dir)) return true
            }
        }
        val handled = super.dispatchKeyEvent(event)
        if (!handled && event.action == KeyEvent.ACTION_DOWN && trap != null) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val currentFocus = trap.findFocus()
                    if (currentFocus == null || currentFocus === trap) trap.requestFocus()
                    return true
                }
            }
        }
        return handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(true)
        window?.apply {
            // Full width, anchored to the bottom, same visual style as a bottom sheet.
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            // Transparent window background — content view sets its own background.
            setBackgroundDrawableResource(android.R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
        }
    }
}

class FullScreenDialog(
    context: Context
) : Dialog(context, context.resolveThemedResourceId(R.attr.fullScreenDialogTheme)) {
    val surface = Surface()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window!!.apply {
            isSystemBarsTranslucentCompat = true

            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )

            decorView.setOnInsertsChangedListener {
                if (surface.insets != it)
                    surface.insets = it
            }
        }
    }
}
