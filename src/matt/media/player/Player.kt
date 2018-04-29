package matt.media.player

import javafx.util.Duration
import java.util.*
import javafx.scene.media.MediaPlayer.Status as Status

object Player
{
    // Stacks used for keeping track of current position in queue
    // A stack structure is used since playlists may contain other playlists
    val playlistStack = Stack<Playlist>()
    val mediaIndexStack = Stack<Int>()
    
    init
    {
        // initial queue setup
        playlistStack.push(Playlist(""))
        mediaIndexStack.push(0)
    }
    
    // keeps track of the currently playing song if there is one
    var currentlyPlaying: MediaHandle? = null
    
    val status
        get() = currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer?.status ?: Status.STOPPED
    
    var volume: Double = 1.0
    
    fun play(song: String)
    {
        clearQueue()
        enqueue(song)
        play()
    }
    
    /**
     * If a song is paused it will continue playing that song. Otherwise it will play the current song in the queue
     * as marked by the top of playlistStack and mediaIndexStack
     */
    tailrec fun play()
    {
        if(status != Status.PLAYING)
        {
            // If we're paused continue playing, otherwise find the next song to play
            currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer?.play() ?: let {
                // If the playlist at the top of the stack is not empty
                if(!playlistStack.peek().isEmpty())
                {
                    // while we haven't found a song to play or a non-empty playlist, check the next song/playlist
                    while(currentlyPlaying == null)
                    {
                        currentlyPlaying = playlistStack.peek().getMedia(mediaIndexStack.peek())
                        if(currentlyPlaying is PlaylistHandle && currentlyPlaying!!.getPlaylist().isRecursivelyEmpty())
                        {
                            currentlyPlaying = null
                            next()
                            if(playlistStack.size == 1 && mediaIndexStack.peek() == 0)
                                return
                        }
                    }
                    // If we found a song, play it
                    if(currentlyPlaying is SongHandle)
                    {
                        val player = currentlyPlaying!!.getCurrentAudioSource().mediaPlayer
                        player.onEndOfMedia = Runnable {next()} // When the song ends it will automatically go to the next song
                        player.volume = volume
                        player.play()
                    }
                    else // Since it's a playlist, push it to the top of the stack and tail-recursively call play() again
                    {
                        playlistStack.push(currentlyPlaying!!.getPlaylist())
                        mediaIndexStack.push(0)
                        currentlyPlaying = null // A playlist cannot be directly played so we need to mark this as null
                        return play() // return is used to make the function tail recursive
                    }
                }
            }
        }
    }
    
    fun pause()
    {
        currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer?.pause()
    }
    
    /**
     * @clearStack If this is true, the position in the queue will be reset to the beginning
     */
    fun stop(clearStack: Boolean = true)
    {
        val player = currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer
        player?.stop()
        player?.onEndOfMedia = null
        currentlyPlaying = null
        
        // Reset the position in the queue to the beginning
        if(clearStack)
        {
            while(playlistStack.size > 1)
                playlistStack.pop()
            mediaIndexStack.clear()
            mediaIndexStack.push(0)
        }
    }
    
    
    fun enqueue(song: String)
    {
        playlistStack[0].addSong(MediaLibrary.loadedAudio[song]!!)
    }
    
    fun clearQueue()
    {
        stop()
        playlistStack.peek().clearPlaylist()
    }
    
    fun next()
    {
        // While we're at the end of the current playlist and we're not at the root playlist, pop the current playlist off of the stack
        while(playlistStack.size > 1 && mediaIndexStack.peek() + 1 == playlistStack.peek().numMediaHandles())
        {
            playlistStack.pop()
            mediaIndexStack.pop()
        }
        
        // If we're still at the end of the playlist then we must be at the root playlist so we should stop playing music
        if(mediaIndexStack.peek() + 1 == playlistStack.peek().numMediaHandles())
        {
            stop()
            return
        }
    
        mediaIndexStack.push(mediaIndexStack.pop() + 1)
        // If a song is currently playing, stop it without resetting the queue position and play the next song
        currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer?.let {
            stop(false)
            play()
        }
    }
    
    private var _previousShouldPlay = false
    tailrec fun previous()
    {
        currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer?.let {
            // If more than 3 seconds have elapsed from the start of the song or we're at the first song in the queue, restart the song
            if(it.currentTime.toSeconds() > 3.0 || (playlistStack.size == 1 && mediaIndexStack.peek() == 0))
            {
                it.seek(Duration.ZERO)
            }
            else // Less than 3 seconds have elapsed since the start of the song and we're not at the very first song
            {
                // Stop the song without resetting the queue position
                stop(false)
                
                // While we're at the beginning of the current playlist and we're not at the root playlist, pop the current playlist off of the stack
                while(playlistStack.size > 1 && mediaIndexStack.peek() == 0)
                {
                    playlistStack.pop()
                    mediaIndexStack.pop()
                }
                
                // If we're not at the beginning of the root playlist then go to the previous song
                if(mediaIndexStack.peek() > 0)
                {
                    mediaIndexStack.push(mediaIndexStack.pop() - 1)
                    
                    val mediaHandle = playlistStack.peek().getMedia(mediaIndexStack.peek())
                    if(mediaHandle is PlaylistHandle && mediaHandle.getPlaylist().isRecursivelyEmpty())
                    {
                        _previousShouldPlay = true
                        
                        return previous()
                    }
                }
                play()
            }
        } ?: let {
            while(playlistStack.size > 1 && mediaIndexStack.peek() == 0)
            {
                playlistStack.pop()
                mediaIndexStack.pop()
            }
    
            // If we're not at the beginning of the root playlist then go to the previous song
            if(mediaIndexStack.peek() > 0)
            {
                mediaIndexStack.push(mediaIndexStack.pop() - 1)
        
                val mediaHandle = playlistStack.peek().getMedia(mediaIndexStack.peek())
                if(mediaHandle is PlaylistHandle && mediaHandle.getPlaylist().isRecursivelyEmpty())
                    return previous()
            }
            
            if(_previousShouldPlay)
            {
                play()
                _previousShouldPlay = false
            }
        }
    }
    
    fun volume(newVolume: Double)
    {
        volume = newVolume
        currentlyPlaying?.getCurrentAudioSource()?.mediaPlayer?.volume = newVolume
    }
}