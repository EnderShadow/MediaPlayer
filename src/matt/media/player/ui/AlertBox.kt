package matt.media.player.ui

import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.scene.text.Text
import javafx.stage.Modality
import javafx.stage.Stage

class AlertBox<T>(title: String, message: String, vararg options: Pair<String, T>)
{
    private val window: Stage = Stage()
    
    var returnValue: T? = null
        private set
    
    init
    {
        window.initModality(Modality.APPLICATION_MODAL)
        window.title = title
        window.minWidth = 250.0
        val label = Text(message)
        label.font = Font.font(label.font.family, 14.0)
        label.wrappingWidth = 500.0
        val buttons = options.map {
            val button = Button(it.first)
            button.setOnAction {_ ->
                returnValue = it.second
                window.close()
            }
            button
        }
        val hbox = HBox(10.0)
        hbox.alignment = Pos.CENTER
        hbox.children.addAll(buttons)
        val layout = VBox(10.0)
        layout.padding = Insets(5.0)
        layout.children.addAll(label, hbox)
        layout.alignment = Pos.CENTER
        window.scene = Scene(layout)
    }
    
    fun showAndWait() = window.showAndWait()
}