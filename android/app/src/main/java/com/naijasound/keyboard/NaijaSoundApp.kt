package com.naijasound.keyboard

import android.app.Application
import android.content.Context

class NaijaSoundApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}

val Context.appGraph: AppGraph
    get() = (applicationContext as NaijaSoundApp).graph
