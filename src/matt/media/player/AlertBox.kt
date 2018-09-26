package matt.media.player

import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
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
        val label = Label(message)
        val buttons = options.map {
            val button = Button(it.first)
            button.setOnAction {_ ->
                returnValue = it.second
                window.close()
            }
            button
        }
        val hbox = HBox(10.0)
        hbox.children.addAll(buttons)
        val layout = VBox(10.0)
        layout.children.addAll(label, hbox)
        layout.alignment = Pos.CENTER
        window.scene = Scene(layout)
    }
    
    fun showAndWait() = window.showAndWait()
}