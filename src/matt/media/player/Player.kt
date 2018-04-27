package matt.media.player

import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import javafx.scene.media.MediaPlayer.Status as Status

object Player
{
    val queue = mutableListOf<AudioSource>()
    var player: MediaPlayer? = null
    
    val status
        get() = player?.status ?: Status.STOPPED
    var songIndex = 0
    var volume: Double = 1.0
    
    fun play(song: String)
    {
        clearQueue()
        enqueue(song)
        play()
    }
    
    fun play()
    {
        if(status != Status.PLAYING)
        {
            player?.play() ?: let {
                if(queue.isNotEmpty())
                {
                    player = queue[songIndex].mediaPlayer
                    player!!.onEndOfMedia = Runnable {next()}
                    player!!.volume = volume
                    player!!.play()
                }
            }
        }
    }
    
    fun pause()
    {
        player?.pause()
    }
    
    fun stop()
    {
        player?.stop()
        player?.onEndOfMedia = null
        player = null
        songIndex = 0
    }
    
    fun enqueue(song: String)
    {
        queue.add(MediaLibrary.loadedAudio[song]!!)
    }
    
    fun clearQueue()
    {
        stop()
        queue.clear()
    }
    
    fun next()
    {
        if(songIndex + 1 >= queue.size)
            stop()
        else if(songIndex + 1 < queue.size)
            player?.let {
                val newIndex = songIndex + 1
                stop()
                songIndex = newIndex
                play()
            }
    }
    
    fun previous()
    {
        player?.let {
            if(it.currentTime.toSeconds() > 3.0 || songIndex == 0)
            {
                it.seek(Duration.ZERO)
            }
            else
            {
                val newIndex = songIndex - 1
                stop()
                songIndex = newIndex
                play()
            }
        }
    }
    
    fun volume(newVolume: Double)
    {
        volume = newVolume
        player?.volume = newVolume
    }
}