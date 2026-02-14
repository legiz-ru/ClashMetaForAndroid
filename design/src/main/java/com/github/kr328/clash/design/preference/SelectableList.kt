package com.github.kr328.clash.design.preference

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KMutableProperty0

interface SelectableListPreference<T> : ClickablePreference {
    var selected: Int

    var listener: OnChangedListener?
}

fun <T> PreferenceScreen.selectableList(
    value: KMutableProperty0<T>,
    values: Array<T>,
    valuesText: Array<Int>,
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    configure: SelectableListPreference<T>.() -> Unit = {},
): SelectableListPreference<T> {
    val impl = object : SelectableListPreference<T>, ClickablePreference by clickable(title, icon) {
        override var selected: Int = 0
            set(value) {
                field = value

                this.summary = context.getText(valuesText[value])
            }
        override var listener: OnChangedListener? = null
    }

    impl.configure()

    launch(Dispatchers.Main) {
        val initial = withContext(Dispatchers.IO) {
            value.get()
        }

        impl.selected = values.indexOf(initial)

        impl.clicked {
            popupSelectMenu(impl, value, context.getText(title), valuesText.map { context.getText(it) }, values)
        }
    }

    return impl
}

private fun <T> PreferenceScreen.popupSelectMenu(
    impl: SelectableListPreference<T>,
    value: KMutableProperty0<T>,
    title: CharSequence,
    valuesText: List<CharSequence>,
    values: Array<T>,
) {
    var selected = impl.selected

    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setSingleChoiceItems(valuesText.toTypedArray(), impl.selected) { _, which ->
            selected = which
        }
        .setNegativeButton(com.github.kr328.clash.design.R.string.cancel, null)
        .setPositiveButton(com.github.kr328.clash.design.R.string.ok) { _, _ ->
            launch(Dispatchers.Main) {
                withContext(Dispatchers.IO) {
                    value.set(values[selected])
                }

                impl.selected = selected
                impl.listener?.onChanged()
            }
        }
        .show()
}
