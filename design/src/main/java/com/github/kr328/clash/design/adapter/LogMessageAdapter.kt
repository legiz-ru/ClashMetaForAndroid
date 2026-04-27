package com.github.kr328.clash.design.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import androidx.core.content.getSystemService
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.databinding.AdapterLogMessageBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class LogMessageAdapter(
    private val context: Context,
    private val copy: (LogMessage) -> Unit,
) : RecyclerView.Adapter<LogMessageAdapter.Holder>() {

    class Holder(val binding: AdapterLogMessageBinding) : RecyclerView.ViewHolder(binding.root)

    var messages: List<LogMessage> = emptyList()

    private val clipboard = context.getSystemService<ClipboardManager>()

    private val ipv4Regex = Regex("""\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?)\b""")
    private val hostRegex = Regex("""(?<![.\d])(?:[a-zA-Z0-9](?:[a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,}""")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AdapterLogMessageBinding.inflate(context.layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = messages[position]

        holder.binding.message = current
        holder.binding.root.setOnLongClickListener {
            copy(current)
            true
        }

        val msgText = current.message
        val spannable = SpannableStringBuilder(msgText)

        val coveredRanges = mutableListOf<IntRange>()

        for (match in ipv4Regex.findAll(msgText)) {
            val range = match.range
            coveredRanges.add(range)
            val ip = match.value
            val span = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    showIpMenu(ip)
                }
            }
            spannable.setSpan(span, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (match in hostRegex.findAll(msgText)) {
            val range = match.range
            if (coveredRanges.any { it.first <= range.first && it.last >= range.last }) continue
            val host = match.value
            val span = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    showHostMenu(host)
                }
            }
            spannable.setSpan(span, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        holder.binding.messageText.text = spannable
        holder.binding.messageText.movementMethod = LinkMovementMethod.getInstance()
    }

    override fun getItemCount(): Int = messages.size

    private fun showIpMenu(ip: String) {
        val items = arrayOf(
            context.getString(R.string.copy_address),
            context.getString(R.string.open_ip_info),
        )
        MaterialAlertDialogBuilder(context)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> clipboard?.setPrimaryClip(ClipData.newPlainText("ip", ip))
                    1 -> context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://ipinfo.io/$ip"))
                    )
                }
            }
            .show()
    }

    private fun showHostMenu(host: String) {
        val items = arrayOf(
            context.getString(R.string.copy_host),
            context.getString(R.string.open_in_browser),
        )
        MaterialAlertDialogBuilder(context)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> clipboard?.setPrimaryClip(ClipData.newPlainText("host", host))
                    1 -> context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://$host"))
                    )
                }
            }
            .show()
    }
}
