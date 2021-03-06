package matt.media.player.ui

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.stage.Modality
import javafx.stage.Stage
import matt.media.player.*
import uk.co.caprica.vlcj.discovery.NativeDiscovery

fun main(args: Array<String>)
{
    System.setProperty("VLCJ_INITX", "no")
    if(args.isNotEmpty() && args[0] == "-d")
        DEBUG = true
    
    if(DEBUG)
        println("Debug mode is enabled")
    
    Application.launch(GUI::class.java, *args)
}

var initializing = true
    private set

class GUI: Application()
{
    override fun start(primaryStage: Stage)
    {
        Config.load()
        NativeLibrary.addSearchPath("libvlc", Config.getString(ConfigKey.VLC_DIRECTORY))
        NativeDiscovery().discover() // discovers VLC
        val loader = FXMLLoader(javaClass.getResource("GUI.fxml"))
        val root: Parent = loader.load()
        primaryStage.title = "Resonant Music Player"
        val scene = Scene(root)
        scene.stylesheets.add("matt/media/player/ui/GUI.css")
        primaryStage.scene = scene
        val controller: Controller = loader.getController()
        controller.window = primaryStage
        controller.settingsWindow = loadSettingsWindow()
        controller.postInit()
        
        val t1 = System.currentTimeMillis()
        
        MediaLibrary.loadingProperty.value = true
        MediaLibrary.loadSongs()
        MediaLibrary.loadPlaylists()
        MediaLibrary.loadingProperty.value = false
        
        println((System.currentTimeMillis() - t1) / 1000.0)
        
        primaryStage.setOnCloseRequest {
            it.consume()
            controller.exit()
        }
        primaryStage.isMaximized = true
        primaryStage.show()
        
        initializing = false
        
        if(!Config.getBoolean(ConfigKey.SUPPRESS_VLC_MESSAGE) && !VLCAudioSource.vlcDetected())
        {
            val bit64 = Native.POINTER_SIZE == 8
            val alertBox = AlertBox("VLC not detected", "Please install VLC. You're computer requires the ${if(bit64) "64" else "32"}-bit version of VLC. While VLC is not required, some media files may not load if it is not installed.", "Ok" to Unit)
            alertBox.showAndWait()
        }
    }
    
    private fun loadSettingsWindow(): Stage
    {
        val stage = Stage()
        stage.initModality(Modality.APPLICATION_MODAL)
        val loader = FXMLLoader(javaClass.getResource("Settings.fxml"))
        val root: Parent = loader.load()
        stage.title = "Settings"
        stage.scene = Scene(root)
        stage.scene.stylesheets.add("matt/media/player/ui/Settings.css")
        val controller: SettingsController = loader.getController()
        controller.window = stage
        
        return stage
    }
}