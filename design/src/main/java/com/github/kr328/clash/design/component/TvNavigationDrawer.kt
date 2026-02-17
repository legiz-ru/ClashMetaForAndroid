package com.github.kr328.clash.design.component

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
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
    var isClashRunning: Boolean = false
        set(value) {
            field = value
            updateToggleButton()
        }

    private var toggleButton: LinearLayout? = null
    private var toggleIcon: ImageView? = null
    private var toggleText: TextView? = null

    fun wrapContent(contentView: View): View {
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

        return wrapper
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
        val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        val onPrimaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
        val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)

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
            background = createFocusableBackground(dp, surfaceVariantColor)
            setOnClickListener { onToggleStatus?.invoke() }
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
            }
            setImageResource(R.drawable.ic_mdi_power)
            imageTintList = ColorStateList.valueOf(onSurfaceColor)
        }
        toggleIcon = icon

        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            this.text = context.getString(R.string.start)
            textSize = 15f
            setTextColor(onSurfaceColor)
        }
        toggleText = text

        item.addView(icon)
        item.addView(text)
        toggleButton = item

        updateToggleButton()
        return item
    }

    private fun updateToggleButton() {
        val dp = context.resources.displayMetrics.density
        val primaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        val onPrimaryColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnPrimary)
        val surfaceVariantColor = context.resolveThemedColor(com.google.android.material.R.attr.colorSurfaceVariant)
        val onSurfaceColor = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurface)

        toggleButton?.let { button ->
            if (isClashRunning) {
                button.background = createFocusableBackground(dp, primaryColor)
                toggleIcon?.imageTintList = ColorStateList.valueOf(onPrimaryColor)
                toggleText?.setTextColor(onPrimaryColor)
                toggleText?.text = context.getString(R.string.stop)
            } else {
                button.background = createFocusableBackground(dp, surfaceVariantColor)
                toggleIcon?.imageTintList = ColorStateList.valueOf(onSurfaceColor)
                toggleText?.setTextColor(onSurfaceColor)
                toggleText?.text = context.getString(R.string.start)
            }
        }
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

    private fun createFocusableBackground(dp: Float, fillColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28 * dp
            setColor(fillColor)
        }
    }
}
