package com.github.kr328.clash.design

import android.content.Context
import android.net.Uri
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.preference.category
import com.github.kr328.clash.design.preference.clickable
import com.github.kr328.clash.design.preference.preferenceScreen
import com.github.kr328.clash.design.preference.tips
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class HelpDesign(
    context: Context,
    openLink: (Uri) -> Unit,
) : Design<Unit>(context) {
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        val screen = preferenceScreen(context) {
            category(R.string.document)

            clickable(
                title = R.string.clash_meta_wiki,
                summary = R.string.clash_meta_wiki_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.clash_meta_wiki_url)))
                }
            }

            category(R.string.sources)

            clickable(
                title = R.string.clash_meta_core,
                summary = R.string.clash_meta_core_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.clash_meta_core_url)))
                }
            }

            clickable(
                title = R.string.clash_meta_for_android,
                summary = R.string.meta_github_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.meta_github_url)))
                }
            }

            clickable(
                title = R.string.moshen_core,
                summary = R.string.moshen_core_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.moshen_core_url)))
                }
            }

            clickable(
                title = R.string.mihomo_smart_core,
                summary = R.string.mihomo_smart_core_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.mihomo_smart_core_url)))
                }
            }

            clickable(
                title = R.string.moshen_fork_core,
                summary = R.string.moshen_fork_core_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.moshen_fork_core_url)))
                }
            }

            clickable(
                title = R.string.cmfa_origin,
                summary = R.string.cmfa_origin_url
            ) {
                clicked {
                    openLink(Uri.parse(context.getString(R.string.cmfa_origin_url)))
                }
            }
        }

        binding.content.addView(screen.root)
    }
}