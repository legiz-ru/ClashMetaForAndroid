package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.preference.*
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.store.ServiceStore

class AppSettingsDesign(
    context: Context,
    uiStore: UiStore,
    srvStore: ServiceStore,
    behavior: Behavior,
    running: Boolean,
    onHideIconChange: (hide: Boolean) -> Unit,
) : Design<AppSettingsDesign.Request>(context) {
    enum class Request {
        ReCreateAllActivities,
        SelectCustomTemplate,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            category(R.string.behavior)

            switch(
                value = behavior::autoRestart,
                icon = R.drawable.ic_baseline_restore,
                title = R.string.auto_restart,
                summary = R.string.allow_clash_auto_restart,
            )

            category(R.string.interface_)

            selectableList(
                value = uiStore::darkMode,
                values = if (uiStore.summerModeUnlocked) DarkMode.values() else arrayOf(DarkMode.Auto, DarkMode.ForceLight, DarkMode.ForceDark),
                valuesText = if (uiStore.summerModeUnlocked) {
                    arrayOf(
                        R.string.follow_system_android_10,
                        R.string.always_light,
                        R.string.always_dark,
                        R.string.always_summer
                    )
                } else {
                    arrayOf(
                        R.string.follow_system_android_10,
                        R.string.always_light,
                        R.string.always_dark
                    )
                },
                icon = R.drawable.ic_baseline_brightness_4,
                title = R.string.dark_mode
            ) {
                listener = OnChangedListener {
                    requests.trySend(Request.ReCreateAllActivities)
                }
            }

            switch(
                value = uiStore::delayDisplayDots,
                icon = R.drawable.ic_baseline_speed,
                title = R.string.delay_display,
                summary = R.string.delay_display_dots,
            )

            switch(
                value = uiStore::hideAppIcon,
                icon = R.drawable.ic_baseline_hide,
                title = R.string.hide_app_icon_title,
                summary = R.string.hide_app_icon_desc,
            ) {
                listener = OnChangedListener {
                    onHideIconChange(uiStore::hideAppIcon.get())
                }
            }

            switch(
                value = uiStore::hideFromRecents,
                icon = R.drawable.ic_baseline_stack,
                title = R.string.hide_from_recents_title,
                summary = R.string.hide_from_recents_desc,
            ) {
                listener = OnChangedListener {
                    requests.trySend(Request.ReCreateAllActivities)
                }
            }

            category(R.string.service)

            switch(
                value = srvStore::dynamicNotification,
                icon = R.drawable.ic_baseline_domain,
                title = R.string.show_traffic,
                summary = R.string.show_traffic_summary
            ) {
                enabled = !running
            }

            category(R.string.privacy)

            switch(
                value = uiStore::sendHwid,
                icon = R.drawable.ic_baseline_assignment,
                title = R.string.send_hwid_title,
                summary = R.string.send_hwid_desc,
            )

            category(R.string.templates)

            clickable(
                title = R.string.custom_template,
                summary = R.string.custom_template_desc,
            ) {
                clicked {
                    requests.trySend(Request.SelectCustomTemplate)
                }
            }
        }

        binding.content.addView(screen.root)
    }
}
