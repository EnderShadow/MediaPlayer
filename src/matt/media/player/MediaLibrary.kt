package matt.media.player

import javafx.application.Platform
import java.io.File
import java.net.URI

object MediaLibrary
{
    val songs = mutableListOf<AudioSource>()
    val songURIMap = mutableMapOf<URI, AudioSource>()
    
    val playlists = mutableListOf<Playlist>()
    
    fun addSong(song: AudioSource)
    {
        songs.add(song)
        songURIMap[song.location] = song
    }
    
    fun loadPlaylists()
    {
        val playlistDir = File(Config.mediaDirectory, "Playlists")
        if(!playlistDir.exists())
            playlistDir.mkdirs()
        
        for(file in playlistDir.listFiles().filter {it.extension == Playlist.EXTENSION})
            if(!isPlaylistLoaded(file.nameWithoutExtension))
                playlists.add(Playlist(file))
    }
    
    fun addPlaylist(playlist: Playlist)
    {
        playlists.add(playlist)
    }
    
    fun isPlaylistLoaded(name: String) = playlists.any {it.name.equals(name, true)}
    
    fun getPlaylist(name: String) = playlists.find {it.name.equals(name, true)}
    
    fun removePlaylist(playlist: Playlist)
    {
        playlists.remove(playlist)
        for(pl in playlists)
            while(pl.containsPlaylist(playlist))
                pl.removePlaylist(playlist)
        File(File(Config.mediaDirectory, "Playlists"), "${playlist.name}.${Playlist.EXTENSION}").delete()
    }
}