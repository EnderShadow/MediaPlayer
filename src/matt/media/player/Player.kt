package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.media.MediaPlayer.Status
import javafx.util.Duration
import java.util.*

object Player
{
    // Stacks used for keeping track of current position in queue
    // A stack structure is used since playlists may contain other playlists
    val rootQueuePlaylist = Playlist("")
    val flatQueue = rootQueuePlaylist.flatView
    private val playlistStack = LinkedList(listOf(rootQueuePlaylist))
    private val mediaIndexStack = LinkedList(listOf(0))
    
    // keeps track of the currently playing song if there is one
    var currentlyPlaying = SimpleObjectProperty<MediaHandle?>(null)
    val playing = SimpleBooleanProperty(false)
    
    init
    {
        val statusListener = InvalidationListener {
            if(currentlyPlaying.value is SongHandle)
                playing.value = currentlyPlaying.value?.getCurrentAudioSource()?.statusProperty?.value == Status.PLAYING
            else
                playing.value = false
        }
        
        currentlyPlaying.addListener {_, oldVal, newVal ->
            if(oldVal is SongHandle)
                oldVal.getCurrentAudioSource().statusProperty.removeListener(statusListener)
            if(newVal is SongHandle)
                newVal.getCurrentAudioSource().statusProperty.addListener(statusListener)
            statusListener.invalidated(null)
        }
    }
    
    val status
        get() = currentlyPlaying.value?.getCurrentAudioSource()?.statusProperty?.value ?: Status.STOPPED
    
    var volume: Double = 1.0
    
    val loopMode = SimpleObjectProperty(LoopMode.NONE)
    val shuffling = SimpleBooleanProperty(false)
    
    /**
     * If a song is paused it will continue playing that song. Otherwise it will play the first song it finds in the queue starting from the current
     * playlist and media index as marked by the top of playlistStack and mediaIndexStack (looping back to the beginning of the queue if necessary)
     */
    fun play()
    {
        if(status != Status.PLAYING)
        {
            // If we're paused continue playing, otherwise find the next song to play
            currentlyPlaying.value?.getCurrentAudioSource()?.play() ?: let {
                // If the queue is not recusively empty
                if(!rootQueuePlaylist.isRecursivelyEmpty())
                {
                    // check if we're at a valid song. If not, find one
                    if(playlistStack.peek().media[mediaIndexStack.peek()] !is SongHandle)
                    {
                        next()
                    }
                    else
                    {
                        currentlyPlaying.value = playlistStack.peek().media[mediaIndexStack.peek()]
                        val audioSource = currentlyPlaying.value!!.getCurrentAudioSource()
                        audioSource.onEndOfMedia = {
                            audioSource.seek(Duration.ZERO)
                            if(loopMode.value == LoopMode.SINGLE)
                            {
                                audioSource.play()
                            }
                            else
                            {
                                next()
                            }
                        } // When the song ends it will automatically go to the next song
                        audioSource.volume = volume
                        audioSource.play()
                    }
                }
            }
        }
    }
    
    fun pause()
    {
        currentlyPlaying.value?.getCurrentAudioSource()?.pause()
    }
    
    /**
     * @clearStack If this is true, the position in the queue will be reset to the beginning
     */
    fun stop(clearStack: Boolean = true)
    {
        currentlyPlaying.value?.getCurrentAudioSource()?.let {
            it.stop()
            it.onEndOfMedia = {}
        }
        currentlyPlaying.value = null
        
        // Reset the position in the queue to the beginning
        if(clearStack)
        {
            while(playlistStack.size > 1)
                playlistStack.pop()
            mediaIndexStack.clear()
            mediaIndexStack.push(0)
        }
    }
    
    
    fun enqueue(mediaHandle: MediaHandle)
    {
        if(mediaHandle is SongHandle)
            enqueue(mediaHandle.getCurrentAudioSource())
        else
            enqueue(mediaHandle.getPlaylist())
    }
    
    fun enqueue(audioSource: AudioSource) = rootQueuePlaylist.addSong(audioSource)
    
    fun enqueue(playlist: Playlist)
    {
        rootQueuePlaylist.addPlaylist(playlist)
        playlist.invalidated(null)
    }
    
    fun clearQueue()
    {
        stop()
        rootQueuePlaylist.clearPlaylist()
    }
    
    // This function will always cause playlistStack.peek() and mediaIndexStack.peek() to point to a song unless the queue is recursively empty
    // in which case it will point to the beginning of the queue
    fun next()
    {
        if(shuffling.value)
            Player.jumpTo((Math.random() * rootQueuePlaylist.size()).toInt())
        else
            nextSong()
    }
    
    private fun nextSong(playAfterFind: Boolean = true)
    {
        // If we're at the start of a playlist and the first element is a playlist make sure we check it.
        playlistStack.peek().media[mediaIndexStack.peek()].takeIf {it is PlaylistHandle && !it.getPlaylist().isRecursivelyEmpty()}?.let {
            mediaIndexStack.push(mediaIndexStack.pop() - 1)
        }
        
        // While we're not at the root playlist and we're at the end of the playlist, go up a level
        while(playlistStack.size > 1 && mediaIndexStack.peek() + 1 >= playlistStack.peek().numMediaHandles())
        {
            playlistStack.pop()
            mediaIndexStack.pop()
        }
        
        // While we haven't searched to the end of the queue...
        while(mediaIndexStack.size > 1 || mediaIndexStack.peek() + 1 < playlistStack.peek().numMediaHandles())
        {
            val currentPlaylist = playlistStack.peek()
            val nextIndex = mediaIndexStack.pop() + 1
            mediaIndexStack.push(nextIndex)
            val foundMedia = currentPlaylist.media[nextIndex]
            if(foundMedia is SongHandle)
            {
                if(status == Status.PLAYING)
                stop(false)
                if(playAfterFind)
                    play()
                return
            }
            else if(!foundMedia.getPlaylist().isRecursivelyEmpty())
            {
                playlistStack.push(foundMedia.getPlaylist())
                mediaIndexStack.push(-1)
            }
            
            // While we're not at the root playlist and we're at the end of the playlist, go up a level
            while(playlistStack.size > 1 && mediaIndexStack.peek() + 1 >= playlistStack.peek().numMediaHandles())
            {
                playlistStack.pop()
                mediaIndexStack.pop()
            }
        }
        stop()
        if(playAfterFind && loopMode.value == LoopMode.ALL)
            play()
    }
    
    // This function will always cause playlistStack.peek() and mediaIndexStack.peek() to point to a song unless the queue is recursively empty
    // in which case it will point to the beginning of the queue. If a song is already playing, it will restart it if has played for more than 3 seconds
    // or it is the first song in the queue
    fun previous()
    {
        currentlyPlaying.value?.getCurrentAudioSource()?.let {
            // If more than 3 seconds have elapsed from the start of the song restart the song
            if(it.currentTimeProperty.value.toSeconds() > 3.0)
            {
                it.seek(Duration.ZERO)
            }
            else // Less than 3 seconds have elapsed since the start of the song
            {
                // Go to the previous song
                previousSong()
            }
        } ?: previousSong()
    }
    
    private fun previousSong()
    {
        // While we're at the beginning of the current playlist and we're not at the root playlist, pop the current playlist off of the stack
        while(playlistStack.size > 1 && mediaIndexStack.peek() == 0)
        {
            playlistStack.pop()
            mediaIndexStack.pop()
        }
    
        // If we're not at the beginning of the root playlist then go to the previous song (or the beginning of the queue if there was no previous song)
        while(playlistStack.size > 1 || mediaIndexStack.peek() != 0)
        {
            // We're not at the beginning of a playlist
            if(mediaIndexStack.peek() > 0)
            {
                mediaIndexStack.push(mediaIndexStack.pop() - 1)
            
                val foundMedia = playlistStack.peek().media[mediaIndexStack.peek()]
                // If we found a playlist that isn't recursively empty, search it in reverse
                if(foundMedia is PlaylistHandle && !foundMedia.getPlaylist().isRecursivelyEmpty())
                {
                    playlistStack.push(foundMedia.getPlaylist())
                    mediaIndexStack.push(foundMedia.getPlaylist().numMediaHandles())
                }
                // We found a song
                else if(foundMedia is SongHandle)
                {
                    return
                }
            }
            // We finished searching through a playlist in reverse
            else
            {
                playlistStack.pop()
                mediaIndexStack.pop()
            }
        }
        
        // We are at the beginning of the queue
        next()
    }
    
    fun volume(newVolume: Double)
    {
        volume = newVolume
        currentlyPlaying.value?.getCurrentAudioSource()?.volume = newVolume
    }
    
    fun jumpTo(songIndex: Int)
    {
        @Suppress("NAME_SHADOWING")
        var songIndex = songIndex
        stop()
        if(rootQueuePlaylist.media[0] is PlaylistHandle)
            nextSong(false)
        while(songIndex-- > 0)
            nextSong(false)
        play()
    }
    
    /**
     * modifies mediaIndexStack so that, after the audioSource is removed from the media player, the media player will work correctly
     */
    fun prepareForRemovalOfSong(audioSource: AudioSource)
    {
        playlistStack.forEachIndexed {index, playlist ->
            val mediaIndexChange = playlist.media.subList(0, mediaIndexStack[index]).count {it is SongHandle && it.getCurrentAudioSource() == audioSource}
            mediaIndexStack[index] -= mediaIndexChange
        }
        if(currentlyPlaying.value?.getCurrentAudioSource() == audioSource)
        {
            currentlyPlaying.value!!.getCurrentAudioSource().let {
                it.onEndOfMedia = {
                    it.seek(Duration.ZERO)
                    if(loopMode.value == LoopMode.SINGLE)
                    {
                        it.play()
                    }
                    else
                    {
                        stop(false)
                        play()
                    }
                }
            }
        }
    }
    
    /**
     * modifies mediaIndexStack so that, after the playlist is removed from the media player, the media player will work correctly
     */
    fun prepareForRemovalOfPlaylist(playlistToRemove: Playlist)
    {
        playlistStack.forEachIndexed {index, playlist ->
            val mediaIndexChange = playlist.media.subList(0, mediaIndexStack[index]).count {it is PlaylistHandle && it.getPlaylist() == playlistToRemove}
            mediaIndexStack[index] -= mediaIndexChange
        }
        val indexOfPlaylist = playlistStack.indexOf(playlistToRemove)
        if(indexOfPlaylist >= 0)
        {
            playlistStack.subList(0, indexOfPlaylist + 1).clear()
            mediaIndexStack.subList(0, indexOfPlaylist + 1).clear()
            Player.currentlyPlaying.value?.getCurrentAudioSource()?.let {
                it.onEndOfMedia = {
                    it.seek(Duration.ZERO)
                    if(loopMode.value == LoopMode.SINGLE)
                    {
                        it.play()
                    }
                    else
                    {
                        stop(false)
                        play()
                    }
                }
            }
        }
    }
    
    /**
     * modifies mediaIndexStack so that, after the media handle is removed from the playlist, the media player will work correctly
     */
    fun prepareForRemovalOfMediaHandleInPlaylist(mediaHandle: MediaHandle, playlistToRemoveFrom: Playlist)
    {
        playlistStack.withIndex().filter {it.value == playlistToRemoveFrom}.forEach {(index, playlist) ->
            if(playlist.media.indexOf(mediaHandle) < mediaIndexStack[index])
                mediaIndexStack[index]--
            else if(playlist.media.indexOf(mediaHandle) == mediaIndexStack[index] && mediaHandle is SongHandle && Player.currentlyPlaying.value == mediaHandle)
            {
                Player.currentlyPlaying.value!!.getCurrentAudioSource().let {
                    it.onEndOfMedia = {
                        it.seek(Duration.ZERO)
                        if(loopMode.value == LoopMode.SINGLE)
                        {
                            it.play()
                        }
                        else
                        {
                            stop(false)
                            play()
                        }
                    }
                }
            }
        }
        if(mediaHandle is PlaylistHandle)
        {
            val indexOfPlaylist = playlistStack.indexOf(mediaHandle.getPlaylist())
            if(indexOfPlaylist >= 0)
            {
                playlistStack.subList(0, indexOfPlaylist + 1).clear()
                mediaIndexStack.subList(0, indexOfPlaylist + 1).clear()
                Player.currentlyPlaying.value?.getCurrentAudioSource()?.let {
                    it.onEndOfMedia = {
                        it.seek(Duration.ZERO)
                        if(loopMode.value == LoopMode.SINGLE)
                        {
                            it.play()
                        }
                        else
                        {
                            stop(false)
                            play()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * modifies mediaIndexStack so that, after a media handle is added to the playlist, the media player will work correctly
     */
    fun prepareForAdditionOfMediaAtInPlaylist(indexToInsertAt: Int, playlistToAddTo: Playlist)
    {
        playlistStack.indices.filter {playlistStack[it] == playlistToAddTo}.forEach {
            if(mediaIndexStack[it] <= indexToInsertAt)
                mediaIndexStack[it]++
        }
    }
    
    /**
     * modifies the mediaIndexStack so that, after the media handles are moved within the playlist, the media player will work correctly
     */
    fun prepareForMovingMediaHandlesInPlaylistTo(mediaHandles: List<MediaHandle>, playlist: Playlist, indexToInsertAt: Int)
    {
        if(playlist in playlistStack)
        {
            val index = playlistStack.indexOf(playlist)
            val currentInMoving = playlist.media[mediaIndexStack[index]] in mediaHandles
            if(currentInMoving)
            {
                mediaIndexStack[index] = indexToInsertAt + mediaHandles.indexOf(playlist.media[mediaIndexStack[index]])
            }
            else
            {
                val numBeforeCurrent = mediaHandles.count {playlistStack[index].indexOf(it) < mediaIndexStack[index]}
                val numBeforeCurrentAfterMove = if(indexToInsertAt <= mediaIndexStack[index]) mediaHandles.size else 0
                val mediaIndexChange = numBeforeCurrentAfterMove - numBeforeCurrent
                mediaIndexStack[index] += mediaIndexChange
            }
        }
    }
}