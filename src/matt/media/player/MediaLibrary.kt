package matt.media.player

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import matt.media.player.music.PlaylistTabController
import java.io.File
import java.lang.IllegalArgumentException
import java.net.URI
import java.util.*
import kotlin.ConcurrentModificationException
import kotlin.concurrent.thread
import kotlin.math.max

object MediaLibrary
{
    val loadingProperty = SimpleBooleanProperty(false)
    private var libraryDirty = false
    
    val songs: ObservableList<AudioSource> = FXCollections.observableArrayList()
    val songURIMap: ObservableMap<URI, AudioSource> = FXCollections.observableHashMap()
    
    val playlists: ObservableList<Playlist> = FXCollections.observableArrayList()
    val playlistIcons: ObservableList<PlaylistTabController.PlaylistIcon> = FXCollections.observableArrayList()
    
    val recentPlaylists: ObservableList<Playlist> = FXCollections.observableList(LinkedList())
    
    init
    {
        Runtime.getRuntime().addShutdownHook(Thread {
            flushLibrary()
            playlists.asSequence().filter {it.dirty}.forEach {it.save(Config.playlistDirectory)}
        })
        
        recentPlaylists.addListener(InvalidationListener {_ ->
            if(recentPlaylists.size > 5)
                recentPlaylists.removeAt(recentPlaylists.lastIndex)
        })
        
        thread(start = true, isDaemon = true) {
            while(true)
            {
                Platform.runLater {try {playlistIcons.forEach {it.invalidated(null)}} catch(cme: ConcurrentModificationException) {}}
                try {Thread.sleep(100)} catch(ie: InterruptedException) {}
            }
        }
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
                addPlaylist(Playlist(file))
        
        recentPlaylists.clear()
        val lastFiveStartIndex = max(playlists.size - 5, 0)
        recentPlaylists.addAll(playlists.subList(lastFiveStartIndex, playlists.size).reversed())
    }
    
    fun addPlaylist(playlist: Playlist)
    {
        val index = Collections.binarySearch(playlists, playlist) {p1, p2 -> p1.name.compareTo(p2.name)}
        playlists.add(-(index + 1), playlist)
        
        playlist.addListener {
            recentPlaylists.remove(playlist)
            recentPlaylists.add(0, playlist)
        }
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
        audioSource.deleteMetadata()
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