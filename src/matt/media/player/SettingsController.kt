package matt.media.player

import javafx.fxml.FXML
import javafx.scene.control.ChoiceBox
import javafx.scene.control.TextField
import javafx.stage.DirectoryChooser
import javafx.stage.Window
import javafx.util.StringConverter

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SettingsController
{
    lateinit var window: Window
    
    private val directoryChooser = DirectoryChooser()
    
    @FXML private lateinit var musicDirectory: TextField
    @FXML private lateinit var vlcDirectory: TextField
    @FXML private lateinit var maxImageSize: TextField
    @FXML private lateinit var maxLoadedSources: TextField
    @FXML private lateinit var defaultPlaylistAddMode: ChoiceBox<Playlist.PlaylistAddMode>
    
    fun initialize()
    {
        reset()
        defaultPlaylistAddMode.items.addAll(Playlist.PlaylistAddMode.values())
        defaultPlaylistAddMode.converter = object: StringConverter<Playlist.PlaylistAddMode>() {
            override fun toString(mode: Playlist.PlaylistAddMode) = mode.toString().toLowerCase().capitalize()
            override fun fromString(mode: String) = Playlist.PlaylistAddMode.valueOf(mode.toUpperCase())
        }
    }
    
    private fun reset()
    {
        directoryChooser.initialDirectory = Config.mediaDirectory
        musicDirectory.text = Config.mediaDirectory.absolutePath
        vlcDirectory.text = Config.vlcDirectory.absolutePath
        maxImageSize.text = Config.maxImageSize.toString()
        maxLoadedSources.text = Config.maxLoadedSources.toString()
        defaultPlaylistAddMode.value = Config.defaultPlaylistAddMode
    }
    
    fun changeMediaDir()
    {
        directoryChooser.showDialog(window)?.let {musicDirectory.text = it.absolutePath}
    }
    
    fun changeVLCDir()
    {
        directoryChooser.showDialog(window)?.let {vlcDirectory.text = it.absolutePath}
    }
    
    fun save()
    {
        var valid = true
        
        if(!isValidFilename(musicDirectory.text))
        {
            valid = false
            if("error" !in musicDirectory.styleClass)
                musicDirectory.styleClass.add("error")
        }
        else
        {
            musicDirectory.styleClass.remove("error")
        }
    
        if(!isValidFilename(vlcDirectory.text))
        {
            valid = false
            if("error" !in vlcDirectory.styleClass)
                vlcDirectory.styleClass.add("error")
        }
        else
        {
            vlcDirectory.styleClass.remove("error")
        }
        
        if(maxImageSize.text.toIntOrNull() == null)
        {
            valid = false
            if("error" !in maxImageSize.styleClass)
                maxImageSize.styleClass.add("error")
        }
        else
        {
            maxImageSize.styleClass.remove("error")
        }
    
        if(maxLoadedSources.text.toIntOrNull()?.let {if(it < 1) null else it} == null)
        {
            valid = false
            if("error" !in maxLoadedSources.styleClass)
                maxLoadedSources.styleClass.add("error")
        }
        else
        {
            maxLoadedSources.styleClass.remove("error")
        }
        
        if(valid)
        {
            val newMusicDirectory = File(musicDirectory.text)
            if(newMusicDirectory.canonicalFile != Config.mediaDirectory.canonicalFile)
            {
                val alertBox = AlertBox("Media Move Prompt", "What would you like to do with your old media?", "Nothing" to MediaMoveOptions.DO_NOTHING, "Copy it" to MediaMoveOptions.COPY, "Move it" to MediaMoveOptions.MOVE)
                alertBox.showAndWait()
        
                when(alertBox.returnValue)
                {
                    null -> valid = false
                    MediaMoveOptions.COPY -> Files.copy(Config.mediaDirectory.toPath(), newMusicDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                    MediaMoveOptions.MOVE ->
                    {
                        Files.copy(Config.mediaDirectory.toPath(), newMusicDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                        val oldDir = Config.mediaDirectory
                        Runtime.getRuntime().addShutdownHook(Thread {oldDir.deleteRecursively()})
                    }
                    MediaMoveOptions.DO_NOTHING -> {}
                }
            }
        }
        
        if(valid)
        {
            Config.mediaDirectory = File(musicDirectory.text)
            Config.vlcDirectory = File(vlcDirectory.text)
            Config.maxImageSize = maxImageSize.text.toInt()
            Config.maxLoadedSources = maxLoadedSources.text.toInt()
            Config.defaultPlaylistAddMode = defaultPlaylistAddMode.value
            Config.updateConfig()
            
            window.hide()
            reset()
        }
    }
    
    fun cancel()
    {
        window.hide()
        reset()
    }
}