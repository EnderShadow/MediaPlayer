package matt.media.player

import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import matt.media.player.music.PlaylistTabController
import java.io.File
import java.lang.IllegalArgumentException
import java.net.URI
import java.util.*

object MediaLibrary
{
    val loadingProperty = SimpleBooleanProperty(false)
    private var libraryDirty = false
    
    val songs: ObservableList<AudioSource> = FXCollections.observableArrayList<AudioSource>()
    val songURIMap: ObservableMap<URI, AudioSource> = FXCollections.observableHashMap<URI, AudioSource>()
    
    val playlists: ObservableList<Playlist> = FXCollections.observableArrayList<Playlist>()
    val playlistIcons: ObservableList<PlaylistTabController.PlaylistIcon> = FXCollections.observableArrayList<PlaylistTabController.PlaylistIcon>()
    
    init
    {
        Runtime.getRuntime().addShutdownHook(Thread {flushLibrary()})
    }
    
    fun refreshPlaylistIcons() = playlistIcons.forEach {it.invalidated(null)}
    
    fun loadSongs()
    {
        Config.libraryFile.forEachLine {
            if(it.isNotBlank())
            {
                try
                {
                    val uri = URI(it)
                    if(isValidAudioFile(uri))
                        addSong(AudioSource.create(uri))
                }
                catch(_: IllegalArgumentException)
                {
                    println("Failed to load song. Maybe it's not a song?: $it")
                }
            }
        }
        libraryDirty = false
    }
    
    fun addSong(song: AudioSource)
    {
        songs.add(song)
        songURIMap[song.location] = song
        libraryDirty = true
    }
    
    fun loadPlaylists()
    {
        for(file in Config.playlistDirectory.listFiles().filter {it.extension == Playlist.EXTENSION})
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
        File(Config.playlistDirectory, "${playlist.name}.${Playlist.EXTENSION}").delete()
    }
    
    fun removeSong(audioSource: AudioSource)
    {
        while(Player.currentlyPlaying.value?.getCurrentAudioSource() == audioSource)
            Player.next()
        playlists.forEach {
            while(it.containsSong(audioSource))
                it.removeSong(audioSource)
        }
        while(Player.queue.containsSong(audioSource))
            Player.queue.removeSong(audioSource)
        songs.remove(audioSource)
        songURIMap.remove(audioSource.location)
        if(!DEBUG)
        {
            libraryDirty = true
        }
        else
        {
            println("$audioSource would have been deleted if debug mode was off")
        }
    }
    
    fun flushLibrary()
    {
        if(libraryDirty)
        {
            Config.libraryFile.writeText(songs.joinToString("\n", postfix = "\n") {it.location.toString()})
            libraryDirty = false
        }
    }
}