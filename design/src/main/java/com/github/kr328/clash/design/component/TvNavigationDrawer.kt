package com.github.kr328.clash.design.component

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.resolveThemedColor

class TvNavigationDrawer(
    private val context: Context,
    private val selectedItem: NavItem,
) {
    enum class NavItem { Home, Profiles, Settings }

    var onNavigate: ((NavItem) -> Unit)? = null
    var onToggleStatus: (() -> Unit)? = null
    var onLatencyTest: (() -> Unit)? = null
    var isClashRunning: Boolean = false
        set(value) {
            field = value
            updateToggleButton()
            updateLatencyTestButton()
        }
    var hasProfiles: Boolean = true
        set(value) {
            field = value
            updateToggleButton()
        }
    var isSimpleMode: Boolean = false
        set(value) {
            field = value
            updateLatencyTestButton()
        }
    var isLatencyTesting: Boolean = false
        set(value) {
            field = value
            updateLatencyTestButton()
        }

    private var toggleButton: LinearLayout? = null
    private var toggleIcon: ImageView? = null
    private var toggleText: TextView? = null
    private var latencyTestButton: LinearLayout? = null

    // D-pad navigation support
    private val drawerFocusableItems = mutableListOf<View>()
    private var selectedNavItemView: View? = null
    private var contentView: View? = null
    private var lastContentFocusRef: java.lang.ref.WeakReference<View>? = null
    private var isRedirectingFocus = false

    /**
     * When set, returns the active dialog's focusable view (e.g. proxy group RecyclerView).
     * When focus escapes to the drawer while the dialog is open, it is redirected here first.
     */
    var dialogFocusTarget: (() -> View?)? = null

    fun withSuppressedFocusRedirect(block: () -> Unit) {
        isRedirectingFocus = true
        try {
            block()
        } finally {
            isRedirectingFocus = false
        }
    }

    fun wrapContent(contentView: View): View {
        this.contentView = contentView
        val dp = context.resources.displayMetrics.density
        val drawerWidth = (260 * dp).toInt()

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val drawer = createDrawer(dp, drawerWidth)
        wrapper.addView(drawer)

        // Vertical divider between drawer and content
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (1 * dp).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(
                context.resolveThemedColor(com.google.android.material.R.attr.colorOutline)
            )
            alpha = 0.3f
        }
        wrapper.addView(divider)

        // Content takes remaining space
        contentView.layoutParams = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        )
        wrapper.addView(contentView)

        // Setup D-pad navigation between drawer and content
        setupDpadNavigation(wrapper)

        return wrapper
    }

    private fun setupDpadNavigation(wrapper: View) {
        // DPAD_RIGHT on any drawer item → focus first focusable in content
        for (item in drawerFocusableItems) {
            item.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    val content = contentView ?: return@setOnKeyListener false
                    val target = content.findFocus() ?: findFirstFocusable(content)
                    if (target != null) {
                        target.requestFocus()
                        return@setOnKeyListener true
                    }
                }
                false
            }
        }

        // When focus moves into content, track it and set nextFocusLeftId to return to selected nav item.
        // When a drawer item steals focus from content (via UP/DOWN traversal or view detachment),
        // redirect back to content or to the active dialog.
        val selectedId = selectedNavItemView?.id ?: return
        wrapper.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            if (isRedirectingFocus) return@addOnGlobalFocusChangeListener
            val content = contentView ?: return@addOnGlobalFocusChangeListener

            if (newFocus != null && isDescendantOf(newFocus, content)) {
                newFocus.nextFocusLeftId = selectedId
                lastContentFocusRef = java.lang.ref.WeakReference(newFocus)
                return@addOnGlobalFocusChangeListener
            }

            val drawerGotFocus = newFocus != null && !isDescendantOf(newFocus, content)
            // Came from content via D-pad (e.g. UP/DOWN leaked into drawer)
            val fromContent = oldFocus != null && isDescendantOf(oldFocus, content)
            // Came from nowhere: previous focus was detached (view removal) or from another window
            val fromDetached = oldFocus == null && lastContentFocusRef?.get() != null

            if (!drawerGotFocus || (!fromContent && !fromDetached)) return@addOnGlobalFocusChangeListener

            // Allow intentional DPAD_LEFT navigation: content item → selectedNavItem only.
            // Any other path (e.g. UP/DOWN reaching the toggle button) should be blocked.
            if (fromContent && newFocus!!.id == selectedId) {
                return@addOnGlobalFocusChangeListener
            }

            // Redirect focus: prefer active dialog, then last content item, then first focusable.
            val dialogView = dialogFocusTarget?.invoke()
            val lastContent = lastContentFocusRef?.get()
            val target = when {
                dialogView != null && dialogView.isAttachedToWindow -> dialogView
                lastContent != null && lastContent.isAttachedToWindow -> lastContent
                fromContent && oldFocus!!.isAttachedToWindow -> oldFocus
                else -> findFirstFocusable(content)
            }
            if (target != null) {
                isRedirectingFocus = true
                target.requestFocus()
                isRedirectingFocus = false
            }
        }
    }

    private fun isDescendantOf(view: View, ancestor: View): Boolean {
        var current: android.view.ViewParent? = view.parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun findFirstFocusable(view: View): View? {
        if (view.isFocusable && view.visibility == View.VISIBLE) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findFirstFocusable(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun createDrawer(dp: Float, width: Int): View {
        val surfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurface)
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT)
            setBackgroundColor(surfaceColor)
            setPadding(0, (24 * dp).toInt(), 0, (24 * dp).toInt())

            // App header
            val header = createHeader(dp)
            addView(header)

            // Spacer
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (16 * dp).toInt()
                )
            })

            // Toggle (Start/Stop) button
            val toggle = createToggleItem(dp)
            addView(toggle)

            // Latency test button (visible only when running + simple mode)
            val latencyTest = createLatencyTestItem(dp)
            addView(latencyTest)

            // Divider
            addView(createItemDivider(dp))

            // Navigation items
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
                isFillViewport = true
            }
            val navContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            navContainer.addView(
                createNavItem(
                    dp,
                    R.drawable.ic_mdi_home,
                    context.getString(R.string.home),
                    selectedItem == NavItem.Home,
                ) { onNavigate?.invoke(NavItem.Home) }
            )
            navContainer.addView(
                createNavItem(
                    dp,
                    R.drawable.ic_mdi_arrange_bring_forward,
                    context.getString(R.string.profiles),
                    selectedItem == NavItem.Profiles,
                ) { onNavigate?.invoke(NavItem.Profiles) }
            )
            navContainer.addView(
                createNavItem(
                    dp,
                    R.drawable.ic_mdi_cog,
                    context.getString(R.string.nav_settings),
                    selectedItem == NavItem.Settings,
                ) { onNavigate?.invoke(NavItem.Settings) }
            )

            scrollView.addView(navContainer)
            addView(scrollView)
        }
    }

    private fun createHeader(dp: Float): View {
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            setPadding((20 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())

            val logo = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()).apply {
                    marginEnd = (12 * dp).toInt()
                }
                setImageResource(R.drawable.ic_clash)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(logo)

            val title = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                text = context.getString(R.string.application_name)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onSurfaceColor)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            addView(title)
        }
    }

    private fun createToggleItem(dp: Float): View {
        val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val transparentColor = 0x00000000

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt(),
            ).apply {
                marginStart = (12 * dp).toInt()
                marginEnd = (12 * dp).toInt()
            }
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            isFocusable = true
            isClickable = true
            background = createFocusableBackground(dp, transparentColor)
            setOnClickListener { onToggleStatus?.invoke() }
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
            }
            setImageResource(R.drawable.ic_mdi_power)
            imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
        }
        toggleIcon = icon

        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            this.text = context.getString(R.string.start)
            textSize = 15f
            setTextColor(onSurfaceVariantColor)
        }
        toggleText = text

        item.addView(icon)
        item.addView(text)
        toggleButton = item

        // Track for D-pad navigation
        item.id = View.generateViewId()
        drawerFocusableItems.add(item)

        return item
    }

    private fun createLatencyTestItem(dp: Float): View {
        val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val transparentColor = 0x00000000

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt(),
            ).apply {
                marginStart = (12 * dp).toInt()
                marginEnd = (12 * dp).toInt()
            }
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            isFocusable = true
            isClickable = true
            background = createFocusableBackground(dp, transparentColor)
            visibility = View.GONE
            setOnClickListener { onLatencyTest?.invoke() }
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
            }
            setImageResource(R.drawable.ic_baseline_speed)
            imageTintList = ColorStateList.valueOf(onSurfaceVariantColor)
        }

        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            this.text = context.getString(R.string.delay_test)
            textSize = 15f
            setTextColor(onSurfaceVariantColor)
        }

        item.addView(icon)
        item.addView(text)
        latencyTestButton = item

        item.id = View.generateViewId()
        drawerFocusableItems.add(item)

        return item
    }

    private fun updateToggleButton() {
        // Hide the Start/Stop control until at least one profile exists.
        toggleButton?.visibility = if (hasProfiles) View.VISIBLE else View.GONE
        toggleText?.text = if (isClashRunning) {
            context.getString(R.string.stop)
        } else {
            context.getString(R.string.start)
        }
    }

    private fun updateLatencyTestButton() {
        val btn = latencyTestButton ?: return
        val visible = isClashRunning && isSimpleMode
        btn.visibility = if (visible) View.VISIBLE else View.GONE
        btn.isEnabled = !isLatencyTesting
        btn.alpha = if (isLatencyTesting) 0.5f else 1f
    }

    private fun createNavItem(
        dp: Float,
        iconRes: Int,
        label: String,
        isSelected: Boolean,
        onClick: () -> Unit,
    ): View {
        val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        val secondaryContainerColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSecondaryContainer)
        val onSecondaryContainerColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSecondaryContainer)
        val onSurfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val transparentColor = 0x00000000

        val item = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt(),
            ).apply {
                marginStart = (12 * dp).toInt()
                marginEnd = (12 * dp).toInt()
            }
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            isFocusable = true
            isClickable = true
            val bgColor = if (isSelected) secondaryContainerColor else transparentColor
            background = createFocusableBackground(dp, bgColor)
            setOnClickListener { onClick() }
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
            }
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                if (isSelected) onSecondaryContainerColor else onSurfaceVariantColor
            )
        }

        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            this.text = label
            textSize = 15f
            if (isSelected) {
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(onSecondaryContainerColor)
            } else {
                setTextColor(onSurfaceVariantColor)
            }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        item.addView(icon)
        item.addView(text)

        // Track for D-pad navigation
        item.id = View.generateViewId()
        drawerFocusableItems.add(item)
        if (isSelected) {
            selectedNavItemView = item
        }

        return item
    }

    private fun createItemDivider(dp: Float): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt(),
            ).apply {
                topMargin = (8 * dp).toInt()
                bottomMargin = (8 * dp).toInt()
                marginStart = (28 * dp).toInt()
                marginEnd = (28 * dp).toInt()
            }
            setBackgroundColor(
                context.resolveThemedColor(com.google.android.material.R.attr.colorOutline)
            )
            alpha = 0.3f
        }
    }

    private fun createFocusableBackground(dp: Float, fillColor: Int): StateListDrawable {
        val focusColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)

        val focusedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28 * dp
            setColor(fillColor)
            setStroke((2 * dp).toInt(), focusColor)
        }

        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28 * dp
            setColor(fillColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }
}
