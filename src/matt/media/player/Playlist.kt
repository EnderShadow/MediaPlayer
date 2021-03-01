package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

private const val PLAYLIST_FORMAT_VERSION = "1.0"

class Playlist private constructor(name: String, description: String, val uuid: UUID, private var initFunction: Playlist.() -> Unit): Observable, InvalidationListener
{
    companion object {
        fun loadPlaylist(path: Path): Playlist {
            when(path.extension) {
                "json" -> {
                    val jsonObj = JSONObject(String(Files.readAllBytes(path)))
                    val version = jsonObj.getString("version")
                    val name = jsonObj.getString("name")
                    val description = jsonObj.getString("description")
                    val uuid = UUID.fromString(jsonObj.getString("uuid"))
                    return Playlist(name, description, uuid) {
                        val sources = jsonObj.getJSONArray("sources")
                        sources.forEach {source ->
                            source as JSONObject
                            val type = source.getString("type")
                            val sourceUUID = UUID.fromString(source.getString("uuid"))
                            when(type) {
                                "song" -> initAddSong(MediaLibrary.sourceUUIDMap[sourceUUID]!!)
                                "playlist" -> initAddPlaylist(MediaLibrary.playlists.first {it.uuid == sourceUUID})
                                else -> System.err.println("Unknown entry type in playlist $name: $type")
                            }
                        }
                        dirty = false
                    }
                }
                "rmppl" -> {
                    return Playlist(path.nameWithoutExtension, "", UUID.randomUUID()) {
                        Files.lines(path).forEach {
                            when {
                                it[0] == 's' -> initAddSong(MediaLibrary.sourceUUIDMap[UUID.fromString(it.substring(1))]!!)
                                it[0] == 'p' -> initAddPlaylist(MediaLibrary.playlists.first {playlist -> playlist.name == it.substring(1)})
                                else -> System.err.println("Unknown entry in ${path.toAbsolutePath()}\n\t$it")
                            }
                        }
                        dirty = true
                        save(path.parent)
                        Files.deleteIfExists(path)
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unsupported playlist extension: ${path.extension}")
                }
            }
        }
        
        fun createPlaylist(name: String, description: String): Playlist {
            return Playlist(name, description, UUID.randomUUID()) {}
        }
    }
    
    private val listeners = mutableListOf<InvalidationListener>()
    
    val flatView by lazy {FlatPlaylistView(this)}
    
    val nameProperty = SimpleStringProperty(name)
    var name: String
        get() = nameProperty.value
        set(value) = nameProperty.set(value)
    
    val descriptionProperty = SimpleStringProperty(description)
    var description: String
        get() = descriptionProperty.value
        set(value) = descriptionProperty.set(value)
    
    // This is used to keep track of the playlists and songs inside this playlist
    private val contents = FXCollections.observableArrayList<MediaHandle>()
    val media: ObservableList<MediaHandle> = FXCollections.unmodifiableObservableList(contents)
    private var numSongs = 0
    private val playlists = mutableListOf<Playlist>()
    var dirty = true
    
    // must be called after instantiation to populate the playlist with it's contents
    fun init() {
        synchronized(this) {
            initFunction()
            initFunction = {}
        }
    }
    
    /**
     * These modes are used for adding playlists to this playlist.
     *
     * REFERENCE: If the playlist being added changes then the changes will be reflected in the playlist it's being added to.
     * CONTENTS: The playlists and songs contained within the playlist being added will be added to the playlist. This is what most media players do.
     * FLATTENED: The playlist being added will be flattened until only songs are left and they will be added.
     */
    enum class PlaylistAddMode
    {
        REFERENCE, CONTENTS, FLATTENED
    }
    
    // Recursively determines the size of the playlist
    fun size(): Int = numSongs + playlists.map {it.size()}.sum()
    
    // The number of MediaHandles directly stored by the playlist
    fun numMediaHandles() = contents.size
    fun isEmpty() = contents.isEmpty()
    
    fun isRecursivelyEmpty(): Boolean = contents.all {it is PlaylistHandle && it.getPlaylist().isRecursivelyEmpty()}
    
    fun getSong(index: Int): SongHandle
    {
        if(index < 0 || index >= size())
            throw ArrayIndexOutOfBoundsException("Cannot get song with index less than 0 or greater than playlist size")
        var curIndex = index
        for(mediaHandle in contents)
        {
            if(mediaHandle is PlaylistHandle)
            {
                val playlist = mediaHandle.getPlaylist()
                if(playlist.size() > curIndex)
                    return playlist.getSong(curIndex)
                else
                    curIndex -= playlist.size()
            }
            else if(curIndex == 0)
            {
                return mediaHandle as SongHandle
            }
            else
            {
                curIndex--
            }
        }
        
        throw IllegalStateException("Somehow we failed to find a song that was in the playlist. This should never happen")
    }
    
    fun getDuration(): Duration = contents.map {
        if(it is SongHandle)
            it.getCurrentAudioSource().durationProperty.value
        else // If it's not a SongHandle it's a PlaylistHandle
            it.getPlaylist().getDuration()
    }.fold(Duration.ZERO, Duration::add)
    
    /**
     * This function is specifically only used by playlists that are loaded from files at program startup. It removes
     * a lot of extra code which isn't needed at this time in order to speed up playlist loading by a factor of approximately 20.
     */
    private fun initAddSong(audioSource: AudioSource) {
        contents.add(SongHandle(audioSource))
        numSongs++
    }
    
    fun addSong(index: Int, audioSource: AudioSource)
    {
        if(index < 0 || index > contents.size)
            throw ArrayIndexOutOfBoundsException("Cannot put AudioSource at index less than 0 or greater than ${contents.size}")
        
        if(contents.isNotEmpty())
            Player.prepareForAdditionOfMediaAtInPlaylist(index, this)
        contents.add(index, SongHandle(audioSource))
        numSongs++
        dirty = true
        invalidated(this)
    }
    
    fun addSong(audioSource: AudioSource) = addSong(contents.size, audioSource)
    
    /**
     * This function is specifically only used by playlists that are loaded from files at program startup. It removes
     * a lot of extra code which isn't needed at this time in order to speed up playlist loading by a factor of approximately 20.
     */
    private fun initAddPlaylist(playlist: Playlist) {
        contents.add(PlaylistHandle(playlist))
        playlists.add(playlist)
        playlist.addListener(this)
    }
    
    fun addPlaylist(index: Int, playlist: Playlist, addMode: PlaylistAddMode = PlaylistAddMode.REFERENCE)
    {
        if(index < 0 || index > contents.size)
            throw ArrayIndexOutOfBoundsException("Cannot put Playlist at index less than 0 or greater than ${contents.size}")
        
        when(addMode)
        {
            PlaylistAddMode.REFERENCE -> {
                // check for recursive playlist
                if(this == playlist || playlist.containsPlaylistRecursive(this))
                    throw IllegalArgumentException("Cannot add a playlist to another playlist such that it loops forever")
    
                if(contents.isNotEmpty())
                    Player.prepareForAdditionOfMediaAtInPlaylist(index, this)
                contents.add(index, PlaylistHandle(playlist))
                playlists.add(playlist)
                playlist.addListener(this)
            }
            PlaylistAddMode.CONTENTS -> {
                for(mediaHandle in playlist.contents.asReversed().toList())
                    if(mediaHandle is SongHandle)
                        addSong(index, mediaHandle.getCurrentAudioSource())
                    else
                        addPlaylist(index, mediaHandle.getPlaylist(), PlaylistAddMode.REFERENCE)
            }
            PlaylistAddMode.FLATTENED -> {
                for(mediaHandle in playlist.contents.asReversed().toList())
                    if(mediaHandle is SongHandle)
                        addSong(index, mediaHandle.getCurrentAudioSource())
                    else
                        addPlaylist(index, mediaHandle.getPlaylist(), PlaylistAddMode.FLATTENED)
            }
        }
        
        dirty = true
        invalidated(this)
    }
    
    fun addPlaylist(playlist: Playlist, addMode: PlaylistAddMode = PlaylistAddMode.REFERENCE) = addPlaylist(contents.size, playlist, addMode)
    
    fun moveMedia(index: Int, mediaHandles: List<MediaHandle>): Int
    {
        if(mediaHandles.isEmpty())
            return index
        if(mediaHandles.any {it !in contents})
            throw IllegalArgumentException("One or more songs is not in the playlist")
        
        Player.prepareForMovingMediaHandlesInPlaylistTo(mediaHandles, this, index)
        val numBefore = mediaHandles.count {contents.indexOf(it) < index}
        contents.removeAll(mediaHandles)
        contents.addAll(index - numBefore, mediaHandles)
        dirty = true
        invalidated(this)
        return index - numBefore
    }
    
    /**
     * The player must have already been told to prepare for removal of a song, playlist, or media handle otherwise the media player will be in an inconsistent state
     */
    private fun removeMedia0(mediaHandle: MediaHandle)
    {
        if(mediaHandle is SongHandle)
            numSongs--
        else
            playlists.remove(mediaHandle.getPlaylist())
        
        contents.remove(mediaHandle)
        dirty = true
        invalidated(this)
    }
    
    fun removeMedia(mediaHandle: MediaHandle)
    {
        if(mediaHandle in contents)
        {
            Player.prepareForRemovalOfMediaHandleInPlaylist(mediaHandle, this)
            removeMedia0(mediaHandle)
            
            val isCurrentMedia = if(mediaHandle is SongHandle)
            {
                Player.currentlyPlaying.value == mediaHandle
            }
            else
            {
                Player.currentlyPlaying.value?.let(mediaHandle.getPlaylist()::containsRecursive) == true
            }
            
            if(isCurrentMedia)
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
        }
    }
    
    /**
     * Player.prepareForRemovalOfSong must be called before this otherwise the media player will be in an inconsistent state
     */
    fun removeSong(audioSource: AudioSource)
    {
        val songHandle = contents.find {it is SongHandle && it.getCurrentAudioSource() == audioSource}
        val playing = Player.currentlyPlaying.value == songHandle
        if(playing)
            Player.stop(false)
        songHandle?.let {removeMedia0(it)}
        if(playing)
            Player.play()
    }
    
    /**
     * Player.prepareForRemovalOfPlaylist must be called before this otherwise the media player will be in an inconsistent state
     */
    fun removePlaylist(playlist: Playlist)
    {
        val playlistHandle = contents.find {it is PlaylistHandle && it.getPlaylist() == playlist}
        playlistHandle?.let {
            playlist.removeListener(this)
            removeMedia0(it)
        }
    }
    
    fun indexOf(mediaHandle: MediaHandle): Int = contents.indexOf(mediaHandle)
    fun indexOf(audioSource: AudioSource): Int = contents.indexOfFirst {it is SongHandle && it.getCurrentAudioSource() == audioSource}
    
    fun containsSong(audioSource: AudioSource): Boolean
    {
        for(mediaHandle in contents)
            if(mediaHandle is SongHandle && mediaHandle.getCurrentAudioSource() == audioSource)
                return true
            else if(mediaHandle is PlaylistHandle && mediaHandle.getPlaylist().containsSong(audioSource))
                return true
        return false
    }
    
    fun containsPlaylist(playlist: Playlist) = playlists.any {it == playlist}
    
    fun containsPlaylistRecursive(playlist: Playlist): Boolean
    {
        if(containsPlaylist(playlist))
            return true
        return playlists.any {it.containsPlaylistRecursive(playlist)}
    }
    
    fun containsRecursive(mediaHandle: MediaHandle): Boolean
    {
        if(mediaHandle in contents)
            return true
        return playlists.any {it.containsRecursive(mediaHandle)}
    }
    
    /**
     * This should never be called unless you are clearing the root queue playlist
     */
    fun clearPlaylist()
    {
        contents.clear()
        playlists.clear()
        numSongs = 0
        dirty = true
        invalidated(this)
    }
    
    fun save(savePath: Path)
    {
        if(Files.notExists(savePath))
            Files.createDirectories(savePath)
        
        val saveLoc = savePath.resolve("$uuid.json")
        val jsonObj = JSONObject()
        jsonObj["version"] = PLAYLIST_FORMAT_VERSION
        jsonObj["name"] = name
        jsonObj["description"] = description
        jsonObj["uuid"] = uuid.toString()
        jsonObj["sources"] = JSONArray(contents.map {
            val source = JSONObject()
            if(it is SongHandle) {
                source["type"] = "song"
                source["uuid"] = it.getCurrentAudioSource().uuid.toString()
            }
            else {
                source["type"] = "playlist"
                source["uuid"] = it.getPlaylist().uuid.toString()
            }
            source
        })
        Files.write(saveLoc, jsonObj.toString().toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        dirty = false
    }
    
    override fun addListener(listener: InvalidationListener)
    {
        listeners.add(listener)
    }
    
    override fun removeListener(listener: InvalidationListener)
    {
        listeners.remove(listener)
    }
    
    override fun invalidated(observable: Observable?)
    {
        retry {listeners.forEach {it.invalidated(this)}}
    }
}

class FlatPlaylistView(val playlist: Playlist): Observable
{
    private val listeners = mutableListOf<InvalidationListener>()
    
    private var songsInternal = mutableListOf<SongHandle>()
    val songs: List<SongHandle>
        get() = songsInternal
    
    init
    {
        reset()
        playlist.addListener {
            reset()
        }
    }
    
    private fun reset()
    {
        val songs = playlist.media.toMutableList()
        var index = 0
        while(index < songs.size)
        {
            if(songs[index] is SongHandle)
            {
                index++
            }
            else
            {
                val media = songs.removeAt(index).getPlaylist().media
                songs.addAll(index, media)
            }
        }
        @Suppress("UNCHECKED_CAST")
        songsInternal = songs as MutableList<SongHandle>
        listeners.forEach {it.invalidated(this)}
    }
    
    override fun removeListener(listener: InvalidationListener)
    {
        listeners.remove(listener)
    }
    
    override fun addListener(listener: InvalidationListener)
    {
        listeners.add(listener)
    }
}