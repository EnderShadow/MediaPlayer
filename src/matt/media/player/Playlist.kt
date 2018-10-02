package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.util.Duration
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class Playlist(name: String): Observable, InvalidationListener
{
    val nameProperty = SimpleStringProperty(name)
    var name
        get() = nameProperty.value
        set(value) = nameProperty.set(value)
    
    constructor(file: File): this(file.nameWithoutExtension)
    {
        Files.lines(file.toPath()).map {URI(it)}.forEach {
            if(isFile(it) && File(it).extension.equals(EXTENSION, true))
            {
                // is a local playlist
                val playlistFile = File(it)
                if(!MediaLibrary.isPlaylistLoaded(playlistFile.nameWithoutExtension))
                {
                    val temp = Playlist(playlistFile)
                    MediaLibrary.addPlaylist(temp)
                    addPlaylist(temp)
                }
                else
                {
                    addPlaylist(MediaLibrary.getPlaylist(playlistFile.nameWithoutExtension)!!)
                }
            }
            else if(it in MediaLibrary.songURIMap)
            {
                // is an already loaded song
                addSong(MediaLibrary.songURIMap[it]!!)
            }
            else
            {
                try
                {
                    // try to load it as a song
                    val song = AudioSource.create(it)
                    MediaLibrary.addSong(song)
                    addSong(song)
                }
                catch(e: Exception)
                {
                    // maybe it's a remote playlist?
                    // TODO try to remotely load playlist
                    System.err.println("Tried to load a remote playlist, but that isn't supported atm.\nURI: $it")
                }
            }
        }
        dirty = false
    }
    
    private val listeners = mutableListOf<InvalidationListener>()
    
    override fun addListener(listener: InvalidationListener)
    {
        listeners.add(listener)
    }
    
    override fun removeListener(listener: InvalidationListener)
    {
        listeners.remove(listener)
    }
    
    override fun invalidated(observable: Observable)
    {
        listeners.forEach {it.invalidated(this)}
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
    
    // Playlist extension
    companion object
    {
        const val EXTENSION = "m3u8"
    }
    
    // This is used to keep track of the playlists and songs inside this playlist
    private val contents = FXCollections.observableArrayList<MediaHandle>()
    val media: ObservableList<MediaHandle> = FXCollections.unmodifiableObservableList(contents)
    private var numSongs = 0
    private val playlists = mutableListOf<Playlist>()
    var dirty = false
    
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
    
    fun addSong(index: Int, audioSource: AudioSource)
    {
        if(index < 0 || index > contents.size)
            throw ArrayIndexOutOfBoundsException("Cannot put AudioSource at index less than 0 or greater than ${contents.size}")
        
        contents.add(index, SongHandle(audioSource))
        numSongs++
        dirty = true
        invalidated(this)
    }
    
    fun addSong(audioSource: AudioSource) = addSong(contents.size, audioSource)
    
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
                
                contents.add(index, PlaylistHandle(playlist))
                playlists.add(playlist)
                playlist.addListener(this)
            }
            PlaylistAddMode.CONTENTS -> {
                for(mediaHandle in playlist.contents.asReversed().toList())
                    if(mediaHandle is SongHandle)
                        addSong(index, mediaHandle.getCurrentAudioSource())
                    else
                        addPlaylist(index, mediaHandle.getPlaylist())
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
        
        val numBefore = mediaHandles.count {contents.indexOf(it) < index}
        contents.removeAll(mediaHandles)
        contents.addAll(index - numBefore, mediaHandles)
        if(Player.currentlyPlaying.value in contents)
        {
            Player.mediaIndexStack.pop()
            Player.mediaIndexStack.push(contents.indexOf(Player.currentlyPlaying.value))
        }
        dirty = true
        return index - numBefore
    }
    
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
            val playing = Player.currentlyPlaying.value == mediaHandle
            if(playing)
                Player.stop(false)
            removeMedia0(mediaHandle)
            if(playing)
                Player.play()
        }
    }
    
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
    
    fun clearPlaylist()
    {
        contents.clear()
        playlists.clear()
        numSongs = 0
        dirty = true
        invalidated(this)
    }
    
    fun save(saveDir: File)
    {
        if(!saveDir.exists())
            saveDir.mkdirs()
        
        val saveLoc = File(saveDir, "$name.$EXTENSION")
        val data = contents.map {
            if(it is SongHandle)
                it.getCurrentAudioSource().location.toString()
            else
                File("${Config.mediaDirectory}${File.separator}Playlists${File.separator}${it.getPlaylist().name}.$EXTENSION").toURI().toString()
        }
        Files.write(saveLoc.toPath(), data, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        dirty = false
    }
    
    fun flatView() = FlatPlaylistView(this)
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