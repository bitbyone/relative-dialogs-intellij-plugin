package one.bitby.relativedialogs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JDialog
import javax.swing.SwingUtilities

class RelativeDialogsStartupActivity : ProjectActivity {
    companion object {
        private val registered = AtomicBoolean(false)
    }

    override suspend fun execute(project: Project) {
        if (!registered.compareAndSet(false, true)) return
        val listener = RelativeDialogsListener()
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.HIERARCHY_EVENT_MASK)
        Disposer.register(ApplicationManager.getApplication()) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            registered.set(false)
        }
    }
}

class RelativeDialogsListener : AWTEventListener {

    companion object {
        // On native Wayland (not XWayland), applications cannot set window position —
        // the compositor controls placement. Detect by checking WAYLAND_DISPLAY is set
        // and we are NOT using the X11 toolkit (i.e. not running through XWayland).
        val isWayland: Boolean =
            System.getenv("WAYLAND_DISPLAY") != null &&
            !Toolkit.getDefaultToolkit().javaClass.name.contains("X11")
    }

    override fun eventDispatched(event: AWTEvent) {
        if (event !is HierarchyEvent) return
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() == 0L) return
        val component = event.source as? Component ?: return
        if (!component.isShowing) return

        // Skip components that live inside the IDE frame (tool windows, panels, editors, etc.)
        // We only handle popups/dialogs in their own separate windows.
        val parentWindow = findParentWindow(component)
        if (parentWindow == null || parentWindow is IdeFrame) return

        val frameBounds = findIdeFrameBounds(component) ?: return

        // Skip if the IDE frame hasn't fully initialized yet (e.g. during startup)
        if (frameBounds.width < 400 || frameBounds.height < 300) return

        val s = RelativeDialogsSettings.getInstance().state

        // Use simpleName to match only the exact class, not inner classes or children.
        // e.g. SearchEverywhereUI$SearchField.simpleName == "SearchField", not "SearchEverywhereUI"
        val simpleName = component::class.java.simpleName
        val className = component::class.java.name

        when {
            (simpleName == "SearchEverywhereUI" || simpleName == "BigPopupUI") ->
                repositionPopup(component, parentWindow, frameBounds, s.searchEverywhere, enforce = true)

            simpleName == "SettingsEditor" ->
                repositionPopup(component, parentWindow, frameBounds, s.settingsEditor)

            simpleName == "SwitcherPanel" ->
                repositionPopup(component, parentWindow, frameBounds, s.switcher)

            simpleName.contains("Bookmark") ->
                repositionPopup(component, parentWindow, frameBounds, s.bookmarks)

            className.contains("FileStructure") ->
                repositionWindowOnly(parentWindow, frameBounds, s.fileStructure)

            className.contains("GitBranches") ->
                repositionWindowOnly(parentWindow, frameBounds, s.gitBranches)

            component is JDialog ->
                repositionDialog(component, frameBounds, s)
        }
    }

    private fun repositionPopup(
        component: Component, win: Window, frameBounds: Rectangle, cfg: RelativeDialogsSettings.DialogConfig, enforce: Boolean = false
    ) {
        if (!cfg.enabled) return
        val bounds = frameBounds.percent(cfg.widthPct, cfg.heightPct, cfg.widthOffset, cfg.heightOffset)
        val action = {
            win.minimumSize = bounds.size
            win.applyBoundsOrSize(bounds, frameBounds)
            component.minimumSize = bounds.size
            component.preferredSize = bounds.size
            component.bounds = Rectangle(0, 0, bounds.width, bounds.height)
            win.revalidate()
        }

        if (enforce) {
            SwingUtilities.invokeLater {
                action()
                // Enforce bounds continuously for a short duration to override internal layout/animations
                val timer = javax.swing.Timer(16) { action() }
                timer.start()
                javax.swing.Timer(400) { timer.stop() }.apply {
                    isRepeats = false
                    start()
                }
            }
            // Persistently prevent the window from collapsing (e.g. when search text is cleared
            // and internal panels unmount). minimumSize alone doesn't help because IntelliJ may
            // call setBounds/setSize directly, bypassing the minimum-size constraint.
            if (win.componentListeners.none { it is SizeEnforcer }) {
                win.addComponentListener(SizeEnforcer(win, bounds))
            }
        } else {
            SwingUtilities.invokeLater { action() }
        }
    }

    private fun repositionWindowOnly(
        win: Window, frameBounds: Rectangle, cfg: RelativeDialogsSettings.DialogConfig
    ) {
        if (!cfg.enabled) return
        val bounds = frameBounds.percent(cfg.widthPct, cfg.heightPct, cfg.widthOffset, cfg.heightOffset)
        SwingUtilities.invokeLater {
            win.applyBoundsOrSize(bounds, frameBounds)
            win.revalidate()
        }
    }

    private fun repositionDialog(dialog: JDialog, frameBounds: Rectangle, s: RelativeDialogsSettings.State) {
        val cfg = when {
            frameBounds.width < RelativeDialogsSettings.BREAKPOINT_MEDIUM -> s.genericDialogSmall
            frameBounds.width < RelativeDialogsSettings.BREAKPOINT_LARGE  -> s.genericDialogMedium
            else                                                           -> s.genericDialogLarge
        }
        if (!cfg.enabled) return
        SwingUtilities.invokeLater {
            val bounds = frameBounds.percent(cfg.widthPct, cfg.heightPct, cfg.widthOffset, cfg.heightOffset)
            dialog.preferredSize = bounds.size
            dialog.applyBoundsOrSize(bounds, frameBounds)
            dialog.revalidate()
        }
    }

    private fun findIdeFrameBounds(component: Component): Rectangle? {
        var window: Window? = if (component is Window) component
            else SwingUtilities.getWindowAncestor(component)
        while (window != null) {
            if (window is IdeFrame && window is Frame) return window.bounds
            window = window.owner
        }
        return Frame.getFrames()
            .filterIsInstance<IdeFrame>()
            .map { it as Frame }
            .firstOrNull { it.isShowing }
            ?.bounds
    }

    private fun findParentWindow(component: Component): Window? {
        if (component is Window) return component
        return SwingUtilities.getWindowAncestor(component)
    }

    // On Wayland, coordinate systems differ by window type:
    //   - xdg_toplevel (decorated): compositor controls position → size only.
    //   - xdg_popup (undecorated): JBR positions via xdg_positioner using coordinates relative
    //     to the parent window surface. Our bounds are in absolute screen coords, so subtract
    //     the frame origin to convert to the parent-relative system.
    private fun Window.applyBoundsOrSize(bounds: Rectangle, frameBounds: Rectangle) {
        when {
            !isWayland -> this.bounds = bounds
            isUndecorated() -> this.bounds = Rectangle(
                bounds.x - frameBounds.x,
                bounds.y - frameBounds.y,
                bounds.width,
                bounds.height
            )
            else -> size = bounds.size
        }
    }

    private fun Window.isUndecorated(): Boolean = when (this) {
        is java.awt.Dialog -> isUndecorated
        is java.awt.Frame  -> isUndecorated
        else               -> true
    }

    private fun Rectangle.center(nwidth: Int, nheight: Int): Rectangle =
        Rectangle(x + (width - nwidth) / 2, y + (height - nheight) / 2, nwidth, nheight)

    private fun Rectangle.percent(xp: Int, yp: Int, xOff: Int = 0, yOff: Int = 0): Rectangle {
        val nw = ((width * xp / 100.0) + xOff).toInt().coerceAtLeast(0)
        val nh = ((height * yp / 100.0) + yOff).toInt().coerceAtLeast(0)
        return center(nw, nh)
    }
}

/**
 * Prevents a window from shrinking below [minBounds] after it has been positioned.
 * IntelliJ's SearchEverywhereUI can call setBounds/setSize directly (bypassing minimumSize)
 * when internal panels unmount (e.g. results list or preview panel on empty search text).
 */
private class SizeEnforcer(private val win: Window, private val minBounds: Rectangle) : ComponentAdapter() {
    private var enforcing = false

    override fun componentResized(e: ComponentEvent) {
        if (enforcing) return
        if (win.width < minBounds.width || win.height < minBounds.height) {
            enforcing = true
            if (RelativeDialogsListener.isWayland) win.size = minBounds.size else win.bounds = minBounds
            enforcing = false
        }
    }
}
