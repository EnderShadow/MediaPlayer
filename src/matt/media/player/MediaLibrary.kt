package matt.media.player

import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.scene.media.MediaException
import java.io.File
import java.net.URI
import java.util.*

object MediaLibrary
{
    val songs = FXCollections.observableArrayList<AudioSource>()
    val songURIMap = FXCollections.observableHashMap<URI, AudioSource>()
    
    val playlists = FXCollections.observableArrayList<Playlist>()
    
    fun loadSongs()
    {
        val queue = LinkedList(listOf(Config.mediaDirectory))
        while(queue.isNotEmpty())
        {
            val curFile = queue.remove()
            if(curFile.isDirectory)
                queue.addAll(curFile.listFiles())
            else
                try
                {
                    addSong(AudioSource(curFile.toURI()))
                }
                catch(me: MediaException)
                {
                    println("Failed to load song. Maybe it's not a song?: ${curFile.absolutePath}")
                }
        }
    }
    
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
    
    fun removeSong(audioSource: AudioSource)
    {
        while(Player.currentlyPlaying.value?.getCurrentAudioSource() == audioSource)
            Player.next()
        playlists.forEach {
            while(it.containsSong(audioSource))
                it.removeSong(audioSource)
        }
        Player.playlistStack.forEach {
            while(it.containsSong(audioSource))
                it.removeSong(audioSource)
        }
        songs.remove(audioSource)
        songURIMap.remove(audioSource.location)
        audioSource.dispose()
        if(isFile(audioSource.location))
        {
            if(!DEBUG)
            {
                val file = File(audioSource.location)
                if(!file.delete())
                    file.deleteOnExit()
            }
            else
            {
                println("$audioSource would have been deleted if debug mode was off")
            }
        }
    }
}