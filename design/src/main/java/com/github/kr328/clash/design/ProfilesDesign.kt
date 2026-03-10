package com.github.kr328.clash.design

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.component.TvNavigationDrawer
import com.github.kr328.clash.design.databinding.DesignProfilesBinding
import com.github.kr328.clash.design.databinding.DesignSheetAddProfileProfilesBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    sealed class Request {
        object UpdateAll : Request()
        object Create : Request()
        object AddFromClipboard : Request()
        object ScanQrCode : Request()
        object AddFromFile : Request()
        object AddManually : Request()
        object GoHome : Request()
        object OpenSettings : Request()
        object ToggleStatus : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
        data class OpenUrl(val url: String) : Request()
        data class ShowAnnounce(val profile: Profile) : Request()
        object TvImport : Request()
    }

    private val binding = DesignProfilesBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = ProfileAdapter(
        context,
        this::requestActive,
        this::showMenu,
        onEditClicked = { requests.trySend(Request.Edit(it)) },
        onDeleteClicked = { requests.trySend(Request.Delete(it)) },
        onUpdateClicked = { profile ->
            if (updatingProfiles.contains(profile.uuid) || allUpdating) {
                showAlreadyUpdatingDialog()
            } else {
                updatingProfiles.add(profile.uuid)
                requests.trySend(Request.Update(profile))
            }
        },
        onAnnounceClicked = { requests.trySend(Request.ShowAnnounce(it)) },
        onSupportClicked = { if (it.supportUrl.isNotEmpty()) requests.trySend(Request.OpenUrl(it.supportUrl)) },
        onWebPageClicked = { if (it.profileWebPageUrl.isNotEmpty()) requests.trySend(Request.OpenUrl(it.profileWebPageUrl)) },
    )

    private var allUpdating: Boolean
        get() = adapter.states.allUpdating;
        set(value) {
            adapter.states.allUpdating = value
        }
    private val updatingProfiles = mutableSetOf<UUID>()
    private val rotateAnimation : Animation = AnimationUtils.loadAnimation(context, R.anim.rotate_infinite)

    private val isTv = TvUtils.isTv(context)

    private val useDrawerNav: Boolean = isTv || run {
        val cfg = context.resources.configuration
        cfg.smallestScreenWidthDp >= 600 &&
            cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private val tvDrawer: TvNavigationDrawer? = if (useDrawerNav) {
        TvNavigationDrawer(context, TvNavigationDrawer.NavItem.Profiles).apply {
            onNavigate = { item ->
                when (item) {
                    TvNavigationDrawer.NavItem.Home -> requests.trySend(Request.GoHome)
                    TvNavigationDrawer.NavItem.Profiles -> {} // Already on profiles
                    TvNavigationDrawer.NavItem.Settings -> requests.trySend(Request.OpenSettings)
                }
            }
            onToggleStatus = { requests.trySend(Request.ToggleStatus) }
        }
    } else null

    private val rootView: View = if (useDrawerNav) {
        tvDrawer!!.wrapContent(binding.root)
    } else {
        binding.root
    }

    override val root: View
        get() = rootView

    fun setClashRunning(running: Boolean) {
        tvDrawer?.isClashRunning = running
    }

    suspend fun patchProfiles(profiles: List<Profile>) {
        adapter.apply {
            patchDataSet(this::profiles, profiles, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }

        withContext(Dispatchers.Main) {
            binding.updateView.visibility = if (updatable) View.VISIBLE else View.GONE
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    fun updateElapsed() {
        adapter.updateElapsed()
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
    }

    private fun showMenu(profile: Profile) {
        val dialog = AppBottomSheetDialog(context)

        val binding = DialogProfilesMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        binding.master = this
        binding.self = dialog
        binding.profile = profile

        dialog.setContentView(binding.root)
        dialog.show()
    }

    fun requestUpdateAll() {
        if (allUpdating) {
            showAlreadyUpdatingDialog()
            return
        }
        allUpdating = true;
        changeUpdateAllButtonStatus()
        requests.trySend(Request.UpdateAll)
    }

    fun finishUpdateAll() {
        allUpdating = false;
        changeUpdateAllButtonStatus()
    }

    fun requestCreate() {
        showAddProfileSheet()
    }

    private fun showAddProfileSheet() {
        val dialog = AppBottomSheetDialog(context)

        val sheetBinding = DesignSheetAddProfileProfilesBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        sheetBinding.master = this
        sheetBinding.dialog = dialog
        sheetBinding.isTv = isTv

        dialog.setContentView(sheetBinding.root)
        dialog.show()
    }

    fun showAnnounceSheet(profile: Profile) {
        if (profile.announce.isEmpty()) return

        MaterialAlertDialogBuilder(context)
            .setTitle(profile.name)
            .setMessage(profile.announce.replace("\\n", "\n"))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    fun requestSheet(dialog: Dialog, request: Request) {
        dialog.dismiss()
        requests.trySend(request)
    }

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    fun requestUpdate(dialog: Dialog, profile: Profile) {
        dialog.dismiss()
        if (updatingProfiles.contains(profile.uuid) || allUpdating) {
            showAlreadyUpdatingDialog()
        } else {
            updatingProfiles.add(profile.uuid)
            requests.trySend(Request.Update(profile))
        }
    }

    fun requestEdit(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))

        dialog.dismiss()
    }

    fun requestDuplicate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Duplicate(profile))

        dialog.dismiss()
    }

    fun requestDelete(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))

        dialog.dismiss()
    }

    fun markProfileFinished(uuid: UUID) {
        updatingProfiles.remove(uuid)
    }

    private fun showAlreadyUpdatingDialog() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.profile_updating_title)
            .setMessage(R.string.profile_updating_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun changeUpdateAllButtonStatus() {
        if (allUpdating) {
            binding.updateView.startAnimation(rotateAnimation)
        } else {
            binding.updateView.clearAnimation()
        }
    }
}
