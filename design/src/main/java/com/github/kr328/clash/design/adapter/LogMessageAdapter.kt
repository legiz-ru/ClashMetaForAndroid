package com.github.kr328.clash.design.adapter

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.design.databinding.AdapterLogMessageBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.showHostMenu
import com.github.kr328.clash.design.util.showIpMenu

class LogMessageAdapter(
    private val context: Context,
    private val copy: (LogMessage) -> Unit,
) : RecyclerView.Adapter<LogMessageAdapter.Holder>() {

    class Holder(val binding: AdapterLogMessageBinding) : RecyclerView.ViewHolder(binding.root)

    var messages: List<LogMessage> = emptyList()

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
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = showIpMenu(context, ip)
            }, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        for (match in hostRegex.findAll(msgText)) {
            val range = match.range
            if (coveredRanges.any { it.first <= range.first && it.last >= range.last }) continue
            val host = match.value
            spannable.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = showHostMenu(context, host)
            }, range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val tv = holder.binding.messageText
        tv.text = spannable
        // LinkMovementMethod auto-sets clickable+focusable on the TextView;
        // explicitly confirm so parent's clickable=true doesn't swallow taps on spans.
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.isClickable = true
        tv.isFocusable = true
    }

    override fun getItemCount(): Int = messages.size
}
