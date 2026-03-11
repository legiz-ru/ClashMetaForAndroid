package com.github.kr328.clash.design.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
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
