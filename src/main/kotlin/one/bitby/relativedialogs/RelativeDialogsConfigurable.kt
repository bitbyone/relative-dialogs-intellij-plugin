package one.bitby.relativedialogs

import com.intellij.openapi.options.Configurable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class RelativeDialogsConfigurable : Configurable {

    private data class ConfigUI(
        val enabled: JCheckBox,
        val wPct: JSpinner,
        val hPct: JSpinner,
        val wOff: JSpinner,
        val hOff: JSpinner
    )

    private lateinit var searchEverywhere: ConfigUI
    private lateinit var settingsEditor: ConfigUI
    private lateinit var switcher: ConfigUI
    private lateinit var bookmarks: ConfigUI
    private lateinit var fileStructure: ConfigUI
    private lateinit var gitBranches: ConfigUI
    private lateinit var genericDialog: ConfigUI
    private var mainPanel: JComponent? = null

    override fun getDisplayName() = "Relative Dialogs"

    override fun createComponent(): JComponent {
        val s = RelativeDialogsSettings.getInstance().state
        searchEverywhere = createConfigUI(s.searchEverywhere)
        settingsEditor = createConfigUI(s.settingsEditor)
        switcher = createConfigUI(s.switcher)
        bookmarks = createConfigUI(s.bookmarks)
        fileStructure = createConfigUI(s.fileStructure)
        gitBranches = createConfigUI(s.gitBranches)
        genericDialog = createConfigUI(s.genericDialog)

        mainPanel = panel {
            buildGroup("Search Everywhere", "Applies to Search Everywhere, Go to Class, Actions, Go to File, etc.", searchEverywhere)
            buildGroup("Settings Editor", "Applies to the main IDE Settings / Preferences dialog.", settingsEditor)
            buildGroup("Switcher", "Applies to the Recent Files / Switcher popup (Ctrl+Tab or Ctrl+E).", switcher)
            buildGroup("Bookmarks", "Applies to the Bookmarks popup.", bookmarks)
            buildGroup("File Structure", "Applies to File Structure (Ctrl+F12) popup.", fileStructure)
            buildGroup("Git Branches", "Applies to Git Branches popup.", gitBranches)
            buildGroup("Generic Dialogs", "Applies to generic dialog windows (e.g. Commit, Push, Refactor). Serves as max bounds.", genericDialog)
        }
        return mainPanel!!
    }

    private fun com.intellij.ui.dsl.builder.Panel.buildGroup(title: String, tooltip: String, ui: ConfigUI) {
        lateinit var checkbox: com.intellij.ui.dsl.builder.Cell<JCheckBox>
        group(title) {
            row {
                checkbox = cell(ui.enabled).comment(tooltip)
            }
            row("Size (%):") {
                cell(ui.wPct)
                label("x")
                cell(ui.hPct)
            }.enabledIf(checkbox.selected)
            row("Offset (px):") {
                cell(ui.wOff)
                label("x")
                cell(ui.hOff)
            }.enabledIf(checkbox.selected)
        }
    }

    override fun isModified(): Boolean {
        val s = RelativeDialogsSettings.getInstance().state
        return searchEverywhere.differs(s.searchEverywhere) ||
            settingsEditor.differs(s.settingsEditor) ||
            switcher.differs(s.switcher) ||
            bookmarks.differs(s.bookmarks) ||
            fileStructure.differs(s.fileStructure) ||
            gitBranches.differs(s.gitBranches) ||
            genericDialog.differs(s.genericDialog)
    }

    override fun apply() {
        val s = RelativeDialogsSettings.getInstance().state
        searchEverywhere.applyTo(s.searchEverywhere)
        settingsEditor.applyTo(s.settingsEditor)
        switcher.applyTo(s.switcher)
        bookmarks.applyTo(s.bookmarks)
        fileStructure.applyTo(s.fileStructure)
        gitBranches.applyTo(s.gitBranches)
        genericDialog.applyTo(s.genericDialog)
    }

    override fun reset() {
        val s = RelativeDialogsSettings.getInstance().state
        searchEverywhere.setFrom(s.searchEverywhere)
        settingsEditor.setFrom(s.settingsEditor)
        switcher.setFrom(s.switcher)
        bookmarks.setFrom(s.bookmarks)
        fileStructure.setFrom(s.fileStructure)
        gitBranches.setFrom(s.gitBranches)
        genericDialog.setFrom(s.genericDialog)
    }

    override fun disposeUIResources() { mainPanel = null }

    // --- helpers ---

    private fun createConfigUI(cfg: RelativeDialogsSettings.DialogConfig) = ConfigUI(
        JCheckBox("Enable this section", cfg.enabled),
        JSpinner(SpinnerNumberModel(cfg.widthPct, 10, 100, 5)),
        JSpinner(SpinnerNumberModel(cfg.heightPct, 10, 100, 5)),
        JSpinner(SpinnerNumberModel(cfg.widthOffset, -5000, 5000, 10)),
        JSpinner(SpinnerNumberModel(cfg.heightOffset, -5000, 5000, 10))
    )

    private fun ConfigUI.differs(cfg: RelativeDialogsSettings.DialogConfig) =
        enabled.isSelected != cfg.enabled ||
        wPct.value != cfg.widthPct ||
        hPct.value != cfg.heightPct ||
        wOff.value != cfg.widthOffset ||
        hOff.value != cfg.heightOffset

    private fun ConfigUI.applyTo(cfg: RelativeDialogsSettings.DialogConfig) {
        cfg.enabled = enabled.isSelected
        cfg.widthPct = wPct.value as Int
        cfg.heightPct = hPct.value as Int
        cfg.widthOffset = wOff.value as Int
        cfg.heightOffset = hOff.value as Int
    }

    private fun ConfigUI.setFrom(cfg: RelativeDialogsSettings.DialogConfig) {
        enabled.isSelected = cfg.enabled
        wPct.value = cfg.widthPct
        hPct.value = cfg.heightPct
        wOff.value = cfg.widthOffset
        hOff.value = cfg.heightOffset
    }
}