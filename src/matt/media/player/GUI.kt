package matt.media.player

import javafx.application.Application
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
    }
}