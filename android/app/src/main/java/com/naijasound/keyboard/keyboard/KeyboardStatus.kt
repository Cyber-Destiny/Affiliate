package com.naijasound.keyboard.keyboard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Whether the Naija Sound keyboard is switched on, and whether it is the one in use. */
object KeyboardStatus {
    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        return imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    fun isSelected(context: Context): Boolean {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return ComponentName.unflattenFromString(id)?.packageName == context.packageName
    }
}
