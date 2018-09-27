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
import java.net.URLDecoder
import kotlin.math.roundToInt

class VLCAudioSource(location: URI): AudioSource(location)
{
    companion object
    {
        fun isSupported(uri: URI) = vlcPlayer != null
        
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
    
                override fun finished(mediaPlayer: MediaPlayer)
                {
                    mediaPlayer.toAudioSource().onEndOfMedia()
                }
    
                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long)
                {
                    Platform.runLater {mediaPlayer.toAudioSource().currentTimeProperty.value = Duration.millis(newTime.toDouble())}
                }
    
                override fun mediaDurationChanged(mediaPlayer: MediaPlayer, newDuration: Long)
                {
                    mediaPlayer.toAudioSource().durationProperty.value = Duration.millis(newDuration.toDouble())
                }
    
                override fun mediaMetaChanged(mediaPlayer: MediaPlayer, metaType: Int)
                {
                    val meta = mediaPlayer.mediaMeta
                    val audioSource = mediaPlayer.toAudioSource()
                    (audioSource as VLCAudioSource).run {
                        durationProperty.value = Duration.millis(meta.length.toDouble())
                        artistProperty.value = meta.artist
                        albumProperty.value = meta.album
                        genreProperty.value = meta.genre
                        albumArtistProperty.value = meta.albumArtist
                        setLoadedImage(meta.artwork)
                        trackCountProperty.value = meta.trackTotal.toInt()
                        trackNumberProperty.value = meta.trackNumber.toInt()
                        yearProperty.value = meta.date
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
        
        fun MediaPlayer.toAudioSource() = MediaLibrary.songs.first {it.titleProperty.value == mediaMeta.title}!!
        
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
        vlcPlayer!!.prepareMedia(media)
        markActive(this)
    }
    
    override fun dispose()
    {
        // does nothing
    }
    
    override fun play()
    {
        if(vlcPlayer!!.mrl() != media.mrl())
            vlcPlayer.playMedia(media)
        else
            vlcPlayer.play()
    }
    
    override fun pause()
    {
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