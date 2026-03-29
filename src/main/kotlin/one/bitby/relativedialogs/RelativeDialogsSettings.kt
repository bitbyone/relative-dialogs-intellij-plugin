package one.bitby.relativedialogs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "one.bitby.relativedialogs.RelativeDialogsSettings",
    storages = [Storage("RelativeDialogs.xml")]
)
class RelativeDialogsSettings : PersistentStateComponent<RelativeDialogsSettings.State> {

    class DialogConfig(
        var enabled: Boolean = true,
        var widthPct: Int = 100,
        var heightPct: Int = 100,
        var widthOffset: Int = 0,
        var heightOffset: Int = 0
    )

    class State {
        var searchEverywhere = DialogConfig(widthPct = 75, heightPct = 85)
        var settingsEditor = DialogConfig(widthPct = 70, heightPct = 90)
        var switcher = DialogConfig(widthPct = 50, heightPct = 70)
        var bookmarks = DialogConfig(widthPct = 70, heightPct = 85)
        var fileStructure = DialogConfig(widthPct = 45, heightPct = 75)
        var gitBranches = DialogConfig(widthPct = 45, heightPct = 75)
        var genericDialog = DialogConfig(widthPct = 70, heightPct = 90)
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(): RelativeDialogsSettings =
            ApplicationManager.getApplication().getService(RelativeDialogsSettings::class.java)
    }
}