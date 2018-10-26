package matt.media.player

import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.ChoiceBox
import javafx.scene.control.TextField
import javafx.stage.DirectoryChooser
import javafx.stage.Window
import javafx.util.StringConverter

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SettingsController
{
    lateinit var window: Window
    
    private val directoryChooser = DirectoryChooser()
    
    @FXML private lateinit var dataDirectory: TextField
    @FXML private lateinit var vlcDirectory: TextField
    @FXML private lateinit var maxImageSize: TextField
    @FXML private lateinit var maxLoadedSources: TextField
    @FXML private lateinit var vlcMessageCheckbox: CheckBox
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
        directoryChooser.initialDirectory = Config.dataDirectory
        dataDirectory.text = Config.dataDirectory.absolutePath
        vlcDirectory.text = Config.vlcDirectory.path
        maxImageSize.text = Config.maxImageSize.toString()
        maxLoadedSources.text = Config.maxLoadedSources.toString()
        vlcMessageCheckbox.isSelected = Config.showVLCMessage
        defaultPlaylistAddMode.value = Config.defaultPlaylistAddMode
    }
    
    fun changeMediaDir()
    {
        directoryChooser.showDialog(window)?.let {dataDirectory.text = it.absolutePath}
    }
    
    fun changeVLCDir()
    {
        directoryChooser.showDialog(window)?.let {vlcDirectory.text = it.absolutePath}
    }
    
    fun save()
    {
        var valid = true
        
        if(!isValidFilename(dataDirectory.text))
        {
            valid = false
            if("error" !in dataDirectory.styleClass)
                dataDirectory.styleClass.add("error")
        }
        else
        {
            dataDirectory.styleClass.remove("error")
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
            try
            {
                val newDataDirectory = File(dataDirectory.text)
                if(newDataDirectory.canonicalFile != Config.dataDirectory.canonicalFile)
                {
                    Files.move(Config.dataDirectory.toPath(), newDataDirectory.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                }
                Config.dataDirectory = newDataDirectory
            }
            catch(ioe: IOException)
            {
                System.err.println("Failed to move media directory")
                ioe.printStackTrace()
            }
            
            Config.vlcDirectory = File(vlcDirectory.text)
            Config.maxImageSize = maxImageSize.text.toInt()
            Config.maxLoadedSources = maxLoadedSources.text.toInt()
            Config.showVLCMessage = vlcMessageCheckbox.isSelected
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