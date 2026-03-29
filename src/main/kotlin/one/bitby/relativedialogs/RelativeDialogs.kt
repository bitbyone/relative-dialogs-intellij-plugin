package one.bitby.relativedialogs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import java.awt.*
import java.awt.event.AWTEventListener
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
                repositionDialog(component, frameBounds, s.genericDialog)
        }
    }

    private fun repositionPopup(
        component: Component, win: Window, frameBounds: Rectangle, cfg: RelativeDialogsSettings.DialogConfig, enforce: Boolean = false
    ) {
        if (!cfg.enabled) return
        val bounds = frameBounds.percent(cfg.widthPct, cfg.heightPct, cfg.widthOffset, cfg.heightOffset)
        val action = {
            win.bounds = bounds
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
            win.bounds = bounds
            win.revalidate()
        }
    }

    private fun repositionDialog(dialog: JDialog, frameBounds: Rectangle, cfg: RelativeDialogsSettings.DialogConfig) {
        if (!cfg.enabled) return
        SwingUtilities.invokeLater {
            val size = dialog.size
            if (size.width <= 0 || size.height <= 0) return@invokeLater

            val scaleFactor = when {
                size.width < 380 && size.height < 250 -> 1.6
                size.width < 800 && size.height < 600 -> 1.4
                else -> 1.2
            }
            var bounds = frameBounds.center(
                (size.width * scaleFactor).toInt(),
                (size.height * scaleFactor).toInt()
            )
            val maxBounds = frameBounds.percent(cfg.widthPct, cfg.heightPct, cfg.widthOffset, cfg.heightOffset)
            if (bounds.width > maxBounds.width || bounds.height > maxBounds.height) {
                bounds = maxBounds
            }
            dialog.preferredSize = bounds.size
            dialog.bounds = bounds
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

    private fun Rectangle.center(nwidth: Int, nheight: Int): Rectangle =
        Rectangle(x + (width - nwidth) / 2, y + (height - nheight) / 2, nwidth, nheight)

    private fun Rectangle.percent(xp: Int, yp: Int, xOff: Int = 0, yOff: Int = 0): Rectangle {
        val nw = ((width * xp / 100.0) + xOff).toInt().coerceAtLeast(0)
        val nh = ((height * yp / 100.0) + yOff).toInt().coerceAtLeast(0)
        return center(nw, nh)
    }
}