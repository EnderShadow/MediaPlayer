package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.util.Duration
import java.util.*
import javafx.scene.media.MediaPlayer.Status as Status

object Player
{
    // Stacks used for keeping track of current position in queue
    // A stack structure is used since playlists may contain other playlists
    val playlistStack = Stack<Playlist>()
    val mediaIndexStack = Stack<Int>()
    
    // keeps track of the currently playing song if there is one
    var currentlyPlaying = SimpleObjectProperty<MediaHandle?>(null)
    val playing = SimpleBooleanProperty(false)
    
    init
    {
        // initial queue setup
        playlistStack.push(Playlist(""))
        mediaIndexStack.push(0)
        
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
                if(!playlistStack[0].isRecursivelyEmpty())
                {
                    currentlyPlaying.value = playlistStack.peek().media[mediaIndexStack.peek()]
                    // We're already at a song
                    if(currentlyPlaying.value is SongHandle)
                    {
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
                    // We're not at a song
                    else
                    {
                        currentlyPlaying.value = null
                        next()
                        play()
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
        val audioSource = currentlyPlaying.value?.getCurrentAudioSource()
        audioSource?.stop()
        audioSource?.onEndOfMedia = {}
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
            playlistStack[0].addSong(mediaHandle.getCurrentAudioSource())
        else
            playlistStack[0].addPlaylist(mediaHandle.getPlaylist())
    }
    
    fun enqueue(audioSource: AudioSource) = playlistStack[0].addSong(audioSource)
    
    fun enqueue(playlist: Playlist) = playlistStack[0].addPlaylist(playlist)
    
    fun clearQueue()
    {
        stop()
        playlistStack.peek().clearPlaylist()
    }
    
    // This function will always cause playlistStack.peek() and mediaIndexStack.peek() to point to a song unless the queue is recursively empty
    // in which case it will point to the beginning of the queue
    fun next(ignoreShuffle: Boolean = false)
    {
        if(!ignoreShuffle && shuffling.value)
        {
            val flatView = playlistStack[0].flatView
            val randIndex = (Math.random() * flatView.songs.size).toInt()
            jumpTo(flatView.songs[randIndex])
            return
        }
        
        // This code deals with the situation where we're at the beginning of the queue but the first MediaHandle is for a playlist and not a song
        playlistStack.peek().media[mediaIndexStack.peek()].takeIf {it is PlaylistHandle && !it.getPlaylist().isRecursivelyEmpty()}?.let {
            mediaIndexStack.push(mediaIndexStack.pop() - 1)
        }
        
        // While we're at the end of the current playlist and we're not at the root playlist, pop the current playlist off of the stack
        while(playlistStack.size > 1 && mediaIndexStack.peek() + 1 >= playlistStack.peek().numMediaHandles())
        {
            playlistStack.pop()
            mediaIndexStack.pop()
        }
        
        // while we haven't searched to the end of the queue...
        while(playlistStack.size > 1 || mediaIndexStack.peek() + 1 < playlistStack.peek().numMediaHandles())
        {
            mediaIndexStack.push(mediaIndexStack.pop() + 1)
            val foundMedia = playlistStack.peek().media[mediaIndexStack.peek()]
            // We found a song
            if(foundMedia is SongHandle)
            {
                if(status == Status.PLAYING)
                {
                    stop(false)
                    play()
                }
                return
            }
            // We found a playlist that isn't recursively empty
            else if(!foundMedia.getPlaylist().isRecursivelyEmpty())
            {
                playlistStack.push(foundMedia.getPlaylist())
                mediaIndexStack.push(-1) // the next iteration will set it to 0
            }
        
            // Check if we're at the end of the playlist
            while(playlistStack.size > 1 && mediaIndexStack.peek() + 1 >= playlistStack.peek().numMediaHandles())
            {
                playlistStack.pop()
                mediaIndexStack.pop()
            }
        }
        
        // We've reached the end of the playlist
        stop()
        if(loopMode.value == LoopMode.ALL)
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
                // Stop the song without resetting the queue position
                stop(false)
                
                // Go to the previous song since there is now no longer a current song to check
                previous()
                
                // We either found a song or are at the beginning of the queue. play() will call next if it is not at a song
                play()
            }
        } ?: let {
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
    }
    
    fun volume(newVolume: Double)
    {
        volume = newVolume
        currentlyPlaying.value?.getCurrentAudioSource()?.volume = newVolume
    }
    
    fun jumpTo(mediaHandle: MediaHandle)
    {
        stop()
        while(Player.playlistStack.peek().media[Player.mediaIndexStack.peek()] != mediaHandle)
            Player.next(true)
        play()
    }
}