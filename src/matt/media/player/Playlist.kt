package matt.media.player

import javafx.util.Duration

class Playlist(val name: String)
{
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
        const val EXTENSION = ".m3u8"
    }
    
    // This is used to keep track of the playlists and songs inside this playlist
    private val contents = mutableListOf<MediaHandle>()
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
    
    fun getMedia(index: Int): MediaHandle
    {
        if(index < 0 || index >= contents.size)
            throw ArrayIndexOutOfBoundsException("Cannot get MediaHandle with index less than 0 or greater than ${contents.size}")
        return contents[index]
    }
    
    fun getDuration(): Duration = contents.map {
        if(it is SongHandle)
            it.getCurrentAudioSource().mediaSource.duration
        else // If it's not a SongHandle it's a PlaylistHandle
            it.getPlaylist().getDuration()
    }.fold(Duration.ZERO) {acc, duration ->  acc.add(duration)}
    
    fun addSong(index: Int, audioSource: AudioSource)
    {
        if(index < 0 || index > contents.size)
            throw ArrayIndexOutOfBoundsException("Cannot put AudioSource at index less than 0 or greater than ${contents.size}")
        
        val prev = if(index == 0) null else contents[index - 1]
        val next = if(index == contents.size) null else contents[index]
        contents.add(index, SongHandle(audioSource, prev, next))
        numSongs++
        dirty = true
    }
    
    fun addSong(audioSource: AudioSource) = addSong(contents.size, audioSource)
    
    /**
     * @byReference if this is true then a reference to the playlist will
     */
    fun addPlaylist(index: Int, playlist: Playlist, addMode: PlaylistAddMode = PlaylistAddMode.REFERENCE)
    {
        if(index < 0 || index > contents.size)
            throw ArrayIndexOutOfBoundsException("Cannot put Playlist at index less than 0 or greater than ${contents.size}")
        
        when(addMode)
        {
            PlaylistAddMode.REFERENCE -> {
                val prev = if(index == 0) null else contents[index - 1]
                val next = if(index == contents.size) null else contents[index]
                contents.add(index, PlaylistHandle(playlist, prev, next))
                playlists.add(playlist)
            }
            PlaylistAddMode.CONTENTS -> {
                for(mediaHandle in playlist.contents.asReversed())
                    if(mediaHandle is SongHandle)
                        addSong(index, mediaHandle.getCurrentAudioSource())
                    else
                        addPlaylist(index, mediaHandle.getPlaylist())
            }
            PlaylistAddMode.FLATTENED -> {
                for(mediaHandle in playlist.contents.asReversed())
                    if(mediaHandle is SongHandle)
                        addSong(index, mediaHandle.getCurrentAudioSource())
                    else
                        addPlaylist(index, mediaHandle.getPlaylist(), PlaylistAddMode.FLATTENED)
            }
        }
        
        dirty = true
    }
    
    fun addPlaylist(playlist: Playlist, addMode: PlaylistAddMode = PlaylistAddMode.REFERENCE) = addPlaylist(contents.size, playlist, addMode)
    
    private fun removeMedia(mediaHandle: MediaHandle)
    {
        val prev = mediaHandle.getPrev()
        val next = mediaHandle.getNext()
        prev?.setNext(next)
        next?.setPrev(prev)
        
        if(mediaHandle is SongHandle)
            numSongs--
        else
            playlists.remove(mediaHandle.getPlaylist())
        
        contents.remove(mediaHandle)
        dirty = true
    }
    
    fun removeSong(audioSource: AudioSource)
    {
        val songHandle = contents.find {it is SongHandle && it.getCurrentAudioSource() == audioSource}
            if(songHandle == Player.currentlyPlaying)
                Player.next()
            songHandle?.let {removeMedia(it)}
    }
    
    fun removePlaylist(playlist: Playlist)
    {
        val playlistHandle = contents.find {it is PlaylistHandle && it.getPlaylist() == playlist}
        playlistHandle?.let {removeMedia(it)}
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
    
    fun clearPlaylist()
    {
        contents.clear()
        playlists.clear()
        numSongs = 0
        dirty = true
    }
}