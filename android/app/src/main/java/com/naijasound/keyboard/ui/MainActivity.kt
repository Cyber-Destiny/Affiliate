package com.naijasound.keyboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.naijasound.keyboard.appGraph
import com.naijasound.keyboard.ui.theme.NaijaSoundTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = appGraph
        setContent {
            NaijaSoundTheme {
                AppRoot(graph)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        appGraph.previewPlayer.stop()
    }
}
