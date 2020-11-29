package matt.media.player.music

import javafx.fxml.FXML
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.stage.Window
import matt.media.player.MediaLibrary
import matt.media.player.Playlist
import matt.media.player.tempDirectory
import java.io.File
import java.lang.Exception

class NewPlaylistController
{
    lateinit var window: Window
    
    var createdPlaylist: Playlist? = null
        private set
    
    @FXML private lateinit var playlistName: TextField
    @FXML private lateinit var playlistDescription: TextArea
    
    fun initialize()
    {
        playlistName.stylesheets.add("matt/media/player/Settings.css")
    }
    
    fun keyReleased(evt: KeyEvent)
    {
        if(evt.code == KeyCode.ENTER)
            createPlaylistPressed()
        else if(evt.code == KeyCode.ESCAPE)
            cancelPressed()
    }
    
    fun createPlaylistPressed()
    {
        val name = playlistName.text
        if(MediaLibrary.playlists.none {it.name == name})
        {
            try
            {
                // make sure file is a valid name
                val tempFile = File(tempDirectory, name)
                tempFile.createNewFile()
                tempFile.delete()
                
                createdPlaylist = Playlist(name, playlistDescription.text)
                window.hide()
            }
            catch(_: Exception)
            {
                if("error" !in playlistName.styleClass)
                    playlistName.styleClass.add("error")
            }
        }
        else
        {
            if("error" !in playlistName.styleClass)
                playlistName.styleClass.add("error")
        }
    }
    
    fun cancelPressed()
    {
        window.hide()
    }
}