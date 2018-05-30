package matt.media.player

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Stage

fun main(args: Array<String>)
{
    Application.launch(GUI::class.java, *args)
}

class GUI: Application()
{
    override fun start(primaryStage: Stage)
    {
        Config.load()
        val loader = FXMLLoader(javaClass.getResource("GUI.fxml"))
        val root: Parent = loader.load()
        primaryStage.title = "Resonant Music Player"
        val scene = Scene(root)
        scene.stylesheets.add("matt/media/player/GUI.css")
        primaryStage.scene = scene
        val controller: Controller = loader.getController()
        controller.window = primaryStage
        
        // TODO load music
        
        primaryStage.setOnCloseRequest {
            it.consume()
            controller.exit()
        }
        primaryStage.show()
    }
}