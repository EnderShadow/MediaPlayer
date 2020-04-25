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
import java.nio.file.Paths
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
        directoryChooser.initialDirectory = Config.getPath(ConfigKey.DATA_DIRECTORY).toFile()
        dataDirectory.text = Config.getString(ConfigKey.DATA_DIRECTORY)
        vlcDirectory.text = Config.getString(ConfigKey.VLC_DIRECTORY)
        maxImageSize.text = Config.getInt(ConfigKey.MAX_IMAGE_SIZE).toString()
        maxLoadedSources.text = Config.getInt(ConfigKey.MAX_LOADED_SOURCES).toString()
        vlcMessageCheckbox.isSelected = Config.getBoolean(ConfigKey.SUPPRESS_VLC_MESSAGE)
        defaultPlaylistAddMode.value = Playlist.PlaylistAddMode.valueOf(Config.getString(ConfigKey.DEFAULT_PLAYLIST_ADD_MODE))
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
                val newDataDirectory = Paths.get(dataDirectory.text)
                if(newDataDirectory != Config.getPath(ConfigKey.DATA_DIRECTORY))
                {
                    Files.move(Config.getPath(ConfigKey.DATA_DIRECTORY), newDataDirectory, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                }
                Config[ConfigKey.DATA_DIRECTORY] = newDataDirectory.toString()
            }
            catch(ioe: IOException)
            {
                System.err.println("Failed to move media directory")
                ioe.printStackTrace()
            }
            
            Config[ConfigKey.VLC_DIRECTORY] = vlcDirectory.text
            Config[ConfigKey.MAX_IMAGE_SIZE] = maxImageSize.text.toInt()
            Config[ConfigKey.MAX_LOADED_SOURCES] = maxLoadedSources.text.toInt()
            Config[ConfigKey.SUPPRESS_VLC_MESSAGE] = vlcMessageCheckbox.isSelected
            Config[ConfigKey.DEFAULT_PLAYLIST_ADD_MODE] = defaultPlaylistAddMode.value.name
            Config.save()
            
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