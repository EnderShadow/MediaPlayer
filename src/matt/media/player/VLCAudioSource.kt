package matt.media.player

import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.scene.media.MediaPlayer.Status
import javafx.util.Duration
import uk.co.caprica.vlcj.binding.internal.libvlc_state_t
import uk.co.caprica.vlcj.player.MediaPlayer
import uk.co.caprica.vlcj.player.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.MediaPlayerFactory
import uk.co.caprica.vlcj.player.media.simple.SimpleMedia
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Exception
import java.net.URI
import java.util.*
import kotlin.math.roundToInt

class VLCAudioSource(location: URI, uuid: UUID): AudioSource(location, uuid)
{
    companion object
    {
        fun isSupported(uri: URI) = vlcDetected()
        fun vlcDetected() = vlcPlayer != null
        
        private val vlcPlayer = try
        {
            MediaPlayerFactory().newHeadlessMediaPlayer()
        }
        catch(e: Exception)
        {
            null
        }
    
        init
        {
            vlcPlayer?.addMediaPlayerEventListener(object: MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer)
                {
                    mediaPlayer.toAudioSource().statusProperty.value = Status.PLAYING
                }
    
                override fun paused(mediaPlayer: MediaPlayer)
                {
                    mediaPlayer.toAudioSource().statusProperty.value = Status.PAUSED
                }
    
                override fun stopped(mediaPlayer: MediaPlayer)
                {
                    mediaPlayer.toAudioSource().statusProperty.value = Status.STOPPED
                }
                
                // Apparently this doesn't gets called
                override fun finished(mediaPlayer: MediaPlayer)
                {
                    //Player.currentlyPlaying.value!!.getCurrentAudioSource().onEndOfMedia()
                }
    
                override fun error(mediaPlayer: MediaPlayer)
                {
                    Player.currentlyPlaying.value!!.getCurrentAudioSource().onEndOfMedia()
                }
    
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long)
                {
                    val audioSource = mediaPlayer.toAudioSource()
                    Platform.runLater {audioSource.currentTimeProperty.value = Duration.millis(newTime.toDouble())}
                    // Since finished never gets called, I treat the song as over when there's less than 600 milliseconds left
                    // since testing has brought me to the conclusion that that's the least you can skip before the song stops playing.
                    // Testing hasn't shown any noticeable parts of a song getting skipped from this.
                    if(audioSource.durationProperty.value.toMillis().toLong() - 600 <= newTime)
                        audioSource.onEndOfMedia()
                }
    
                override fun mediaDurationChanged(mediaPlayer: MediaPlayer, newDuration: Long)
                {
                    mediaPlayer.toAudioSource().durationProperty.value = Duration.millis(newDuration.toDouble())
                }
    
                override fun mediaMetaChanged(mediaPlayer: MediaPlayer, metaType: Int)
                {
                    val meta = mediaPlayer.mediaMeta
                    val audioSource = mediaPlayer.toAudioSource()
                    audioSource.run {
                        durationProperty.value = Duration.millis(meta.length.toDouble())
                        artistProperty.value = meta.artist ?: ""
                        albumProperty.value = meta.album ?: ""
                        genreProperty.value = meta.genre ?: ""
                        albumArtistProperty.value = meta.albumArtist ?: ""
                        setLoadedImage(meta.artwork)
                        trackCountProperty.value = meta.trackTotal?.toInt() ?: 0
                        trackNumberProperty.value = meta.trackNumber?.toInt() ?: 0
                        yearProperty.value = meta.date ?: ""
                    }
                }
    
                override fun mediaStateChanged(mediaPlayer: MediaPlayer, newState: Int)
                {
                    mediaPlayer.toAudioSource().statusProperty.let {
                        it.value = when(libvlc_state_t.state(newState)!!)
                        {
                            libvlc_state_t.libvlc_NothingSpecial -> it.value
                            libvlc_state_t.libvlc_Opening -> Status.UNKNOWN
                            libvlc_state_t.libvlc_Buffering -> Status.STALLED
                            libvlc_state_t.libvlc_Playing -> Status.PLAYING
                            libvlc_state_t.libvlc_Paused -> Status.PAUSED
                            libvlc_state_t.libvlc_Stopped -> Status.STOPPED
                            libvlc_state_t.libvlc_Ended -> Status.STOPPED
                            libvlc_state_t.libvlc_Error -> Status.HALTED
                        }
                    }
                }
            })
        }
        
        fun MediaPlayer.toAudioSource() = MediaLibrary.songs.first {it is VLCAudioSource && mediaMeta.title == it.titleProperty.value} as VLCAudioSource
        
        fun shutdown()
        {
            vlcPlayer?.release()
        }
    }
    
    private val media = SimpleMedia(File(location).absolutePath)
    
    override var volume: Double
        get() = vlcPlayer!!.volume / 100.0
        set(value)
        {
            vlcPlayer!!.volume = (value * 100.0).roundToInt()
        }
    
    private fun setLoadedImage(image: BufferedImage?)
    {
        if(image == null)
            return
        loadImage = {
            imageProperty.value = squareAndCache(SwingFXUtils.toFXImage(image, null))
            loadImage = {}
        }
    }
    
    override fun init()
    {
        val currentState = Player.status
        vlcPlayer!!.prepareMedia(media)
        markActive(this)
        if((currentState == Status.PLAYING || currentState == Status.PAUSED) && Player.currentlyPlaying.value!!.getCurrentAudioSource() is VLCAudioSource)
        {
            val time = Player.currentlyPlaying.value!!.getCurrentAudioSource().currentTimeProperty.value
            Player.stop(false)
            Player.play()
            Player.currentlyPlaying.value!!.getCurrentAudioSource().seek(time)
            if(currentState == Status.PAUSED)
                Player.pause()
        }
    }
    
    override fun dispose()
    {
        // does nothing
    }
    
    override fun play()
    {
        if(statusProperty.value == Status.PAUSED)
            vlcPlayer!!.pause()
        else
            vlcPlayer!!.playMedia(media)
    }
    
    override fun pause()
    {
        if(statusProperty.value != Status.PAUSED)
            vlcPlayer!!.pause()
    }
    
    override fun stop()
    {
        vlcPlayer!!.stop()
    }
    
    override fun seek(position: Duration)
    {
        vlcPlayer!!.time = position.toMillis().toLong()
    }
}