package matt.media.player

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import javafx.scene.media.MediaPlayer
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
    val songUUIDMap: ObservableMap<UUID, AudioSource> = FXCollections.observableHashMap()
    
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
                try {Thread.sleep(1000)} catch(ie: InterruptedException) {}
            }
        }
    }
    
    fun refreshPlaylistIcons() = playlistIcons.forEach {it.invalidated(null)}
    
    fun loadSongs()
    {
        val notFoundUris = mutableListOf<Pair<UUID, URI>>()
        Config.libraryFile.forEachLine {
            if(it.isNotBlank())
            {
                val (uuid, uriPath) = it.split(" ", limit = 2)
                try
                {
                    val uri = URI(uriPath)
                    if(isValidAudioFile(uri))
                        if(testUri(uri))
                            addSong(AudioSource.create(uri, UUID.fromString(uuid)))
                        else
                            notFoundUris.add(Pair(UUID.fromString(uuid), uri))
                }
                catch(_: IllegalArgumentException)
                {
                    println("Failed to load song. Maybe it's not a song?: $uriPath")
                }
            }
        }
        if(notFoundUris.isNotEmpty())
        {
            val alertBox = AlertBox("Missing songs found", "${notFoundUris.size} songs are in your library but cannot be found.", "Remove them" to MissingSongStrategy.REMOVE, "I'll tell you where they are" to MissingSongStrategy.LOCATE, "Exit the media player" to MissingSongStrategy.EXIT, "Ignore them" to MissingSongStrategy.IGNORE)
            alertBox.showAndWait()
            when(alertBox.returnValue)
            {
                MissingSongStrategy.REMOVE -> libraryDirty = true
                MissingSongStrategy.EXIT -> {
                    libraryDirty = false
                    System.exit(0)
                }
                MissingSongStrategy.IGNORE -> {
                    notFoundUris.forEach {(uuid, uri) -> addSong(NOPAudioSource(uri, uuid))}
                    libraryDirty = false
                }
                MissingSongStrategy.LOCATE -> {
                    // TODO have user locate songs
                }
            }
        }
        else
        {
            libraryDirty = false
        }
    }
    
    fun testUri(uri: URI): Boolean
    {
        return if(uri.scheme.equals("file", true))
            File(uri).exists()
        else
            try
            {
                uri.toURL().openStream().close()
                true
            }
            catch(_: Throwable)
            {
                false
            }
    }
    
    fun addSong(song: AudioSource)
    {
        songs.add(song)
        songUUIDMap[song.uuid] = song
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
    
    fun getOrLoadPlaylist(name: String): Playlist
    {
        return playlists.find {it.name.equals(name, true)} ?: addPlaylist(Playlist(File(Config.playlistDirectory, "$name.${Playlist.EXTENSION}")))
    }
    
    fun addPlaylist(playlist: Playlist): Playlist
    {
        val index = Collections.binarySearch(playlists, playlist) {p1, p2 -> p1.name.compareTo(p2.name)}
        playlists.add(-(index + 1), playlist)
        
        playlist.addListener {
            recentPlaylists.remove(playlist)
            recentPlaylists.add(0, playlist)
        }
        
        return playlist
    }
    
    fun isPlaylistLoaded(name: String) = playlists.any {it.name.equals(name, true)}
    
    fun removePlaylist(playlist: Playlist)
    {
        Player.prepareForRemovalOfPlaylist(playlist)
        playlists.remove(playlist)
        for(pl in playlists)
            while(pl.containsPlaylist(playlist))
                pl.removePlaylist(playlist)
        Player.rootQueuePlaylist.let {
            while(it.containsPlaylist(playlist))
                it.removePlaylist(playlist)
        }
        
        // Explicit comparison with true because it's a nullable boolean
        if(Player.currentlyPlaying.value?.let(playlist::containsRecursive) == true)
        {
            if(Player.status == MediaPlayer.Status.PLAYING)
            {
                Player.stop(false)
                Player.play()
            }
            else
            {
                Player.stop(false)
            }
        }
        File(Config.playlistDirectory, "${playlist.name}.${Playlist.EXTENSION}").delete()
    }
    
    fun removeSong(audioSource: AudioSource)
    {
        Player.prepareForRemovalOfSong(audioSource)
        playlists.forEach {
            while(it.containsSong(audioSource))
                it.removeSong(audioSource)
        }
        while(Player.rootQueuePlaylist.containsSong(audioSource))
            Player.rootQueuePlaylist.removeSong(audioSource)
    
        if(Player.currentlyPlaying.value?.getCurrentAudioSource() == audioSource)
        {
            if(Player.status == MediaPlayer.Status.PLAYING)
            {
                Player.stop(false)
                Player.play()
            }
            else
            {
                Player.stop(false)
            }
        }
        
        songs.remove(audioSource)
        songUUIDMap.remove(audioSource.uuid)
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
            Config.libraryFile.writeText(songs.joinToString("\n", postfix = "\n") {"${it.uuid} ${it.location}"})
            libraryDirty = false
        }
    }
}