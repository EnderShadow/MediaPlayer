package matt.media.player

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.collections.ObservableMap
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import matt.media.player.music.PlaylistTabController
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.net.URI
import java.nio.file.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import kotlin.ConcurrentModificationException
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.system.exitProcess

private const val LIBRARY_FORMAT_VERSION = "1.2"

object MediaLibrary
{
    val loadingProperty = SimpleBooleanProperty(false)
    private var libraryDirty = false
    
    val sources: ObservableList<AudioSource> = FXCollections.observableArrayList()
    val sourceUUIDMap: ObservableMap<UUID, AudioSource> = FXCollections.observableHashMap()
    
    val playlists: ObservableList<Playlist> = FXCollections.observableArrayList()
    val playlistIcons: ObservableList<PlaylistTabController.PlaylistIcon> = FXCollections.observableArrayList()
    
    val recentPlaylists: ObservableList<Playlist> = FXCollections.observableList(LinkedList())
    
    init
    {
        Runtime.getRuntime().addShutdownHook(Thread {
            flushLibrary()
            playlists.asSequence().filter {it.dirty}.forEach {it.save(playlistDirectory)}
        })
        
        recentPlaylists.addListener(InvalidationListener {
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
    
    fun loadSongs() {
        // check for existing or empty library file
        if(Files.notExists(libraryFile) || Files.size(libraryFile) == 0L)
            return
        
        val libraryJson = JSONObject(String(Files.readAllBytes(libraryFile)))
        val libraryVersion = libraryJson.getString("version")
        
        val songsListJson = libraryJson.getJSONArray("songs")
        songsListJson.forEach {
            val songJson = it as JSONObject
            try {
                val uuid = UUID.fromString(songJson.getString("uuid"))
                val uri = URI(songJson.getString("uri"))
                
                val audioSourceFactory = AudioSourceFactory(uri, uuid)
                
                if(libraryVersion < "1.1") {
                    val metadataFile = mediaDirectory.resolve("$uuid.metadata")
    
                    if(Files.exists(metadataFile)) try
                    {
                        DataInputStream(Files.newInputStream(metadataFile, StandardOpenOption.READ).buffered()).use {dataInputStream ->
                            audioSourceFactory.title = dataInputStream.readUTF()
                            audioSourceFactory.artist = dataInputStream.readUTF()
                            audioSourceFactory.album = dataInputStream.readUTF()
                            audioSourceFactory.genre = dataInputStream.readUTF()
                            audioSourceFactory.albumArtist = dataInputStream.readUTF()
                            audioSourceFactory.trackCount = dataInputStream.readInt()
                            audioSourceFactory.trackNumber = dataInputStream.readInt()
                            audioSourceFactory.year = dataInputStream.readUTF()
                            audioSourceFactory.duration = Duration.millis(dataInputStream.readDouble())
                        }
                        
                        Files.delete(metadataFile)
                        markDirty()
                    }
                    catch(ioe: IOException)
                    {
                        System.err.println("Failed to read song metadata from file")
                    }
                }
                else {
                    audioSourceFactory.title = songJson.getString("title")
                    audioSourceFactory.artist = songJson.getString("artist")
                    audioSourceFactory.album = songJson.getString("album")
                    audioSourceFactory.genre = songJson.getString("genre")
                    audioSourceFactory.albumArtist = songJson.getString("albumArtist")
                    audioSourceFactory.trackCount = songJson.getInt("trackCount")
                    audioSourceFactory.trackNumber = songJson.getInt("trackNumber")
                    audioSourceFactory.year = songJson.getString("year")
                    audioSourceFactory.duration = Duration.millis(songJson.getDouble("duration"))
                    if(libraryVersion >= "1.2")
                        audioSourceFactory.dateAdded = LocalDateTime.ofEpochSecond(songJson.getLong("dateAdded"), songJson.getInt("dateAddedNano"), ZoneOffset.UTC)
                }
                
                if(isValidAudioUri(uri))
                    addSource(audioSourceFactory.build(!testUri(uri)))
            }
            catch(_: IllegalArgumentException) {
                println("Failed to load song. Maybe it's not a song?: ${songJson.getString("uri")}")
            }
        }
        
        if(sources.any {it is NOPAudioSource}) {
            val nopSources = sources.filterIsInstance(NOPAudioSource::class.java)
            val alertBox = AlertBox("Missing songs found", "${nopSources.size} songs are in your library but cannot be found.", "Remove them" to MissingSongStrategy.REMOVE, "I'll tell you where they are" to MissingSongStrategy.LOCATE, "Exit the media player" to MissingSongStrategy.EXIT, "Ignore them" to MissingSongStrategy.IGNORE)
            alertBox.showAndWait()
            when(alertBox.returnValue ?: MissingSongStrategy.IGNORE) {
                MissingSongStrategy.REMOVE -> markDirty()
                MissingSongStrategy.EXIT -> {
                    libraryDirty = false
                    exitProcess(0)
                }
                MissingSongStrategy.IGNORE -> {} // songs are already an instance of NOPAudioSource
                MissingSongStrategy.LOCATE -> {
                    // TODO have user locate songs
                }
            }
        }
    }
    
    private fun testUri(uri: URI): Boolean
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
    
    fun addSource(source: AudioSource)
    {
        sources.add(source)
        sourceUUIDMap[source.uuid] = source
        markDirty()
    }
    
    /**
     * returns true if nopSource was successfully replaced
     */
    fun replaceNOPSource(nopSource: NOPAudioSource, newSourceUri: URI): Boolean {
        val index = sources.indexOf(nopSource)
        // if nopSource is not in the library, do not add it
        if(index < 0)
            return false
        val newSource = AudioSourceFactory(nopSource).build(false)
        sources[index] = newSource
        sourceUUIDMap[newSource.uuid] = newSource
        markDirty()
        return true
    }
    
    fun loadPlaylists()
    {
        for(path in Files.newDirectoryStream(playlistDirectory).filter {it.extension == Playlist.EXTENSION})
            if(!isPlaylistLoaded(path.nameWithoutExtension))
                addPlaylist(Playlist(path))
        
        recentPlaylists.clear()
        val lastFiveStartIndex = max(playlists.size - 5, 0)
        recentPlaylists.addAll(playlists.subList(lastFiveStartIndex, playlists.size).reversed())
    }
    
    fun getOrLoadPlaylist(name: String): Playlist
    {
        return playlists.find {it.name.equals(name, true)} ?: addPlaylist(Playlist(playlistDirectory.resolve("$name.${Playlist.EXTENSION}")))
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
    
    private fun isPlaylistLoaded(name: String) = playlists.any {it.name.equals(name, true)}
    
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
        Files.deleteIfExists(playlistDirectory.resolve("${playlist.name}.${Playlist.EXTENSION}"))
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
        
        sources.remove(audioSource)
        sourceUUIDMap.remove(audioSource.uuid)
        if(!DEBUG)
        {
            markDirty()
        }
        else
        {
            println("$audioSource would have been deleted if debug mode was off")
        }
    }
    
    
    fun markDirty() {
        libraryDirty = true
    }
    
    fun flushLibrary()
    {
        if(!libraryDirty)
            return
        
        val songListJson = JSONArray()
        sources.forEach {
            val songJson = JSONObject()
            songJson["uuid"] = it.uuid.toString()
            songJson["uri"] = it.location.toString()
            songJson["title"] = it.titleProperty.value
            songJson["artist"] = it.artistProperty.value
            songJson["album"] = it.albumProperty.value
            songJson["genre"] = it.genreProperty.value
            songJson["albumArtist"] = it.albumArtistProperty.value
            songJson["trackCount"] = it.trackCountProperty.value
            songJson["trackNumber"] = it.trackNumberProperty.value
            songJson["year"] = it.yearProperty.value
            songJson["duration"] = it.durationProperty.value.toMillis()
            songJson["dateAdded"] = it.dateAdded.toEpochSecond(ZoneOffset.UTC)
            songJson["dateAddedNano"] = it.dateAdded.nano
            songListJson.put(songJson)
        }
        val libraryJson = JSONObject()
        libraryJson.put("version", LIBRARY_FORMAT_VERSION)
        libraryJson.put("songs", songListJson)
        
        Files.move(libraryFile, Paths.get(libraryFile.parent.toString(), "${libraryFile.fileName}.old"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Files.write(libraryFile, libraryJson.toString().toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        libraryDirty = false
    }
}