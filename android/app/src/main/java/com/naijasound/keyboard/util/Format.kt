package com.naijasound.keyboard.util

import java.util.Locale

/** 950 → "950", 1200 → "1.2k", 1500000 → "1.5M". */
fun formatCount(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> compact(n / 1_000.0, "k")
    else -> compact(n / 1_000_000.0, "M")
}

private fun compact(value: Double, suffix: String): String {
    val text = if (value >= 100 || value % 1.0 < 0.05) {
        String.format(Locale.US, "%.0f", Math.floor(value))
    } else {
        String.format(Locale.US, "%.1f", Math.floor(value * 10) / 10)
    }
    return text + suffix
}

fun sendsLabel(n: Long): String = if (n == 1L) "1 send" else "${formatCount(n)} sends"
