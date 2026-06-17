package com.github.kr328.clash.design.dialog

import android.content.Context
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun Context.requestModelTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String {
    return this.requestModelTextInput(initial, title, null, hint, error, validator)!!
}

suspend fun Context.requestModelTextInput(
    initial: String?,
    title: CharSequence,
    reset: CharSequence?,
    hint: CharSequence? = null,
    error: CharSequence? = null,
    validator: Validator = ValidatorAcceptAll,
): String? {
    return suspendCancellableCoroutine {
        val view = layoutInflater.inflate(R.layout.dialog_text_field, root, false)
        val textLayout = view.findViewById<TextInputLayout>(R.id.text_layout)
        val textField = view.findViewById<TextInputEditText>(R.id.text_field)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(view)
            .setCancelable(true)
            .setPositiveButton(R.string.ok) { _, _ ->
                val text = textField.text?.toString() ?: ""

                if (validator(text))
                    it.resume(text)
                else
                    it.resume(initial)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnDismissListener { _ ->
                if (!it.isCompleted)
                    it.resume(initial)
            }

        if (reset != null) {
            builder.setNeutralButton(reset) { _, _ ->
                it.resume(null)
            }
        }

        val dialog = builder.create()

        it.invokeOnCancellation {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            if (hint != null)
                textLayout.hint = hint

            textField.apply {
                textLayout.isErrorEnabled = error != null

                doOnTextChanged { text, _, _, _ ->
                    if (!validator(text?.toString() ?: "")) {
                        if (error != null)
                            textLayout.error = error

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                    } else {
                        if (error != null)
                            textLayout.error = null

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }

                setText(initial)

                setSelection(0, initial?.length ?: 0)

                requestTextInput()
            }
        }

        dialog.show()
    }
}

suspend fun Context.requestMultilineTextInput(
    initial: String,
    title: CharSequence,
    hint: CharSequence? = null,
): String {
    return suspendCancellableCoroutine { cont ->
        val view = layoutInflater.inflate(R.layout.dialog_text_field, root, false)
        val textLayout = view.findViewById<TextInputLayout>(R.id.text_layout)
        val textField = view.findViewById<TextInputEditText>(R.id.text_field)

        textField.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        textField.minLines = 4
        textField.maxLines = 10
        textLayout.isErrorEnabled = false

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(view)
            .setCancelable(true)
            .setPositiveButton(R.string.ok) { _, _ ->
                cont.resume(textField.text?.toString() ?: initial)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .setOnDismissListener { if (!cont.isCompleted) cont.resume(initial) }
            .create()

        cont.invokeOnCancellation { dialog.dismiss() }

        dialog.setOnShowListener {
            if (hint != null) textLayout.hint = hint
            textField.setText(initial)
            textField.setSelection(initial.length)
        }

        dialog.show()
    }
}
