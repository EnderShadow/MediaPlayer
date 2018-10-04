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
    val queue = Playlist("")
    val flatQueue = queue.flatView
    var queueIndex = 0
    
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
                if(flatQueue.songs.isNotEmpty())
                {
                    currentlyPlaying.value = flatQueue.songs[queueIndex]
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
            queueIndex = 0
        }
    }
    
    
    fun enqueue(mediaHandle: MediaHandle)
    {
        if(mediaHandle is SongHandle)
            queue.addSong(mediaHandle.getCurrentAudioSource())
        else
            queue.addPlaylist(mediaHandle.getPlaylist())
    }
    
    fun enqueue(audioSource: AudioSource) = queue.addSong(audioSource)
    
    fun enqueue(playlist: Playlist) = queue.addPlaylist(playlist)
    
    fun clearQueue()
    {
        stop()
        queue.clearPlaylist()
    }
    
    // This function will always cause playlistStack.peek() and mediaIndexStack.peek() to point to a song unless the queue is recursively empty
    // in which case it will point to the beginning of the queue
    fun next()
    {
        if(shuffling.value)
        {
            val randIndex = (Math.random() * flatQueue.songs.size).toInt()
            jumpTo(flatQueue.songs[randIndex])
            return
        }
        
        if(queueIndex + 1 < flatQueue.songs.size)
        {
            queueIndex++
            stop(false)
            play()
        }
        else
        {
            stop()
            if(loopMode.value == LoopMode.ALL)
                play()
        }
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
                if(queueIndex > 0)
                    queueIndex--
                play()
            }
        } ?: let {
            if(queueIndex > 0)
                queueIndex--
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
        queueIndex = flatQueue.songs.indexOf(mediaHandle)
        play()
    }
}