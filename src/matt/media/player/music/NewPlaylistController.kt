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
    
    fun createPlaylistPressed()
    {
        val name = playlistName.text
        if(MediaLibrary.playlists.none {it.name == name})
        {
            createdPlaylist = Playlist(name)
            window.hide()
        }
    }
    
    fun cancelPressed()
    {
        window.hide()
    }
}