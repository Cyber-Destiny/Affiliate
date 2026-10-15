package com.naijasound.keyboard.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.naijasound.keyboard.appGraph
import com.naijasound.keyboard.ui.theme.NaijaSoundTheme

/**
 * The keyboard Android shows inside chat apps. It draws with Compose, so it
 * provides the lifecycle pieces an Activity would normally provide.
 */
class SoundKeyboardService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val modelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = modelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var controller: KeyboardController

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        controller = KeyboardController(this, appGraph, lifecycleScope)
    }

    override fun onCreateInputView(): View {
        window.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@SoundKeyboardService)
            setViewTreeViewModelStoreOwner(this@SoundKeyboardService)
            setViewTreeSavedStateRegistryOwner(this@SoundKeyboardService)
            setContent {
                NaijaSoundTheme {
                    KeyboardUi(controller)
                }
            }
        }
    }

    // Never take over the whole screen in landscape; the chat should stay visible.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        if (!restarting) controller.onShown()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.onHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        modelStore.clear()
        super.onDestroy()
    }

    /** Back to the keyboard the user had before (the ABC key). */
    fun switchToPreviousKeyboard() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            switchToLastInputMethodLegacy()
        }
        if (!switched) showKeyboardPicker()
    }

    @Suppress("DEPRECATION")
    private fun switchToLastInputMethodLegacy(): Boolean {
        val token = window.window?.attributes?.token ?: return false
        return getSystemService(InputMethodManager::class.java)?.switchToLastInputMethod(token) == true
    }

    fun showKeyboardPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}
