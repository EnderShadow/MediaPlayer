package matt.media.player.music

import javafx.fxml.FXML
import javafx.scene.control.TextArea
import javafx.scene.control.TextField
import javafx.stage.Window
import matt.media.player.MediaLibrary
import matt.media.player.Playlist

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
    
    fun createPlaylistPressed()
    {
        val name = playlistName.text
        if(MediaLibrary.playlists.none {it.name == name})
        {
            createdPlaylist = Playlist(name)
            window.hide()
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