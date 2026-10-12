package io.github.gitjacobbit.whoscalling.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import kotlinx.coroutines.flow.MutableStateFlow

/** Owns the single overlay window. Must be used from the main thread. */
class OverlayController(context: Context) {
    class Actions(
        val onClose: () -> Unit,
        val onOverride: (e164: String, UserOverride) -> Unit,
    )

    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val state = MutableStateFlow<OverlayUiState?>(null)
    private var view: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var drawn = false
    private var pendingOnVisible: (() -> Unit)? = null

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP
        y = (appContext.resources.displayMetrics.heightPixels * 0.12f).toInt()
    }

    /** [onVisible] runs once the window has drawn its first frame (immediately if it is already showing). */
    fun show(initial: OverlayUiState, actions: Actions, onVisible: () -> Unit = {}) {
        state.value = initial
        if (view != null) {
            if (drawn) onVisible() else pendingOnVisible = onVisible
            return
        }
        pendingOnVisible = onVisible
        val owner = OverlayLifecycleOwner().also { it.start() }
        val composeView = ComposeView(appContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val current by state.collectAsState()
                current?.let { ui ->
                    OverlayCard(
                        state = ui,
                        onClose = actions.onClose,
                        onToggle = { update { it.copy(expanded = !it.expanded) } },
                        onOverride = { override -> ui.e164?.let { actions.onOverride(it, override) } },
                        onDrag = ::moveBy,
                    )
                }
            }
        }
        composeView.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var fired = false
            override fun onDraw() {
                if (fired) return
                fired = true
                // Listeners cannot be removed from inside onDraw; posting also lets the frame reach the screen.
                composeView.post {
                    composeView.viewTreeObserver.removeOnDrawListener(this)
                    if (view !== composeView) return@post
                    drawn = true
                    pendingOnVisible?.invoke()
                    pendingOnVisible = null
                }
            }
        })
        windowManager.addView(composeView, params)
        view = composeView
        lifecycleOwner = owner
    }

    fun update(transform: (OverlayUiState) -> OverlayUiState) {
        state.value?.let { state.value = transform(it) }
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        lifecycleOwner?.destroy()
        view = null
        lifecycleOwner = null
        drawn = false
        pendingOnVisible = null
        state.value = null
    }

    private fun moveBy(dy: Float) {
        val v = view ?: return
        val maxY = appContext.resources.displayMetrics.heightPixels - v.height
        params.y = (params.y + dy.toInt()).coerceIn(0, maxOf(0, maxY))
        windowManager.updateViewLayout(v, params)
    }
}

/** Compose needs a lifecycle and saved-state owner; a window added from outside an Activity has neither. */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
