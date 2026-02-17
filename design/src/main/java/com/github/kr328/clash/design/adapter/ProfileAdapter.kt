package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.model.ProxyPageState
import com.github.kr328.clash.design.ui.ObservableCurrentTime
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.card.MaterialCardView

class ProfileAdapter(
    private val context: Context,
    private val onClicked: (Profile) -> Unit,
    private val onMenuClicked: (Profile) -> Unit,
    private val onEditClicked: ((Profile) -> Unit)? = null,
    private val onDeleteClicked: ((Profile) -> Unit)? = null,
    private val onUpdateClicked: ((Profile) -> Unit)? = null,
    private val onAnnounceClicked: ((Profile) -> Unit)? = null,
    private val onSupportClicked: ((Profile) -> Unit)? = null,
    private val onWebPageClicked: ((Profile) -> Unit)? = null,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root)

    private val currentTime = ObservableCurrentTime()
    private val isTv = TvUtils.isTv(context)

    var profiles: List<Profile> = emptyList()
    val states = ProfilePageState()

    fun updateElapsed() {
        currentTime.update()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterProfileBinding
                .inflate(context.layoutInflater, parent, false)
                .also { binding ->
                    binding.currentTime = currentTime
                    // On TV: allow D-pad to reach action buttons inside the card
                    if (isTv) {
                        val card = binding.rootView
                        if (card is ViewGroup) {
                            card.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
                        }
                    }
                }
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = profiles[position]
        val binding = holder.binding

        if (current === binding.profile)
            return

        binding.profile = current
        binding.setClicked {
            onClicked(current)
        }
        binding.setMenu {
            onMenuClicked(current)
        }
        binding.setEditAction {
            (onEditClicked ?: onMenuClicked)(current)
        }
        binding.setDeleteAction {
            (onDeleteClicked ?: onMenuClicked)(current)
        }
        binding.setUpdateAction {
            (onUpdateClicked ?: onMenuClicked)(current)
        }
        binding.setAnnounceAction {
            onAnnounceClicked?.invoke(current)
        }
        binding.setSupportAction {
            onSupportClicked?.invoke(current)
        }
        binding.setWebPageAction {
            onWebPageClicked?.invoke(current)
        }

        // Active profile card gets a primary-colored stroke
        val card = binding.rootView
        if (card is MaterialCardView) {
            val dp = context.resources.displayMetrics.density
            if (current.active) {
                card.strokeWidth = (2 * dp).toInt()
                card.strokeColor = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
            } else {
                card.strokeWidth = 0
            }
        }
    }

    override fun getItemCount(): Int {
        return profiles.size
    }
}
