package com.naijasound.keyboard.util

import com.naijasound.keyboard.data.ApiException
import com.naijasound.keyboard.data.NotConfiguredException
import java.io.IOException

/** Short, human error text for the screen. */
fun friendlyError(error: Throwable): String = when (error) {
    is NotConfiguredException -> "This build isn't connected to a sound library yet."
    is ApiException -> "The server had a problem (${error.code}). Try again soon."
    is IOException -> "No connection. Check your data or Wi-Fi."
    else -> "Something went wrong. Try again."
}
