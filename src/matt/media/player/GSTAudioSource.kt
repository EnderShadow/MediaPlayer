package matt.media.player

import javafx.application.Platform
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import org.freedesktop.gstreamer.*
import org.freedesktop.gstreamer.elements.PlayBin
import org.freedesktop.gstreamer.event.SeekFlags
import java.net.URI
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate

class GSTAudioSource(location: URI, uuid: UUID, dateAdded: LocalDateTime, title: String, artist: String, album: String, genre: String, albumArtist: String, trackCount: Int, trackNumber: Int, year: String, duration: Duration): AudioSource(location, uuid, dateAdded, title, artist, album, genre, albumArtist, trackCount, trackNumber, year, duration) {
    companion object {
        fun isSupported(uri: URI) = gstDetected()
        fun gstDetected() = Gst.isInitialized()
        
        init {
            try {
                Gst.init("Resonant Media Player")
            }
            catch(_: GstException) {}
        }
    
        private val playBin by lazy {PlayBin("Player")}
        
        private var currentGSTAudioSource: GSTAudioSource? = null
    
        private val positionTimer = Timer(true)
        
        init {
            if(gstDetected()) {
                playBin.setVideoSink(ElementFactory.make("fakesink", "videosink"))
                playBin.connect(PlayBin.ABOUT_TO_FINISH {
                    Platform.runLater {
                        currentGSTAudioSource?.onEndOfMedia?.invoke()
                    }
                })
                playBin.bus.connect {_, _, current: State, _ ->
                    when(current) {
                        State.VOID_PENDING -> {}
                        State.NULL -> currentGSTAudioSource?.statusProperty?.value = MediaPlayer.Status.STOPPED
                        State.READY -> currentGSTAudioSource?.statusProperty?.value = MediaPlayer.Status.READY
                        State.PAUSED -> currentGSTAudioSource?.statusProperty?.value = MediaPlayer.Status.PAUSED
                        State.PLAYING -> currentGSTAudioSource?.statusProperty?.value = MediaPlayer.Status.PLAYING
                    }
                }
                playBin.bus.connect(Bus.TAG {_, tagList ->
                    currentGSTAudioSource?.run {
                        tagList.tagNames.forEach {tagName ->
                            when(tagName) {
                                "title" -> tagList.getValues(tagName).map(Any::toString).firstOrNull(String::isNotBlank)?.let(titleProperty::set)
                                "artist" -> tagList.getValues(tagName).map(Any::toString).firstOrNull(String::isNotBlank)?.let(artistProperty::set)
                                "album" -> tagList.getValues(tagName).map(Any::toString).firstOrNull(String::isNotBlank)?.let(albumProperty::set)
                                "genre" -> tagList.getValues(tagName).map(Any::toString).firstOrNull(String::isNotBlank)?.let(genreProperty::set)
                                "image" -> tagList.getValues(tagName).forEach {println("image: $it")}
                                "track-number" -> tagList.getValues(tagName).mapNotNull {it.toString().toIntOrNull()}.firstOrNull()?.let(trackNumberProperty::set)
                            }
                        }
                    }
                })
    
                positionTimer.scheduleAtFixedRate(10, 10) {
                    val duration = playBin.queryDuration(TimeUnit.MILLISECONDS)
                    if(duration > 0)
                        currentGSTAudioSource?.durationProperty?.value = Duration.millis(duration.toDouble())
                    val position = playBin.queryPosition(TimeUnit.MILLISECONDS)
                    currentGSTAudioSource?.currentTimeProperty?.value = Duration.millis(position.toDouble())
                }
            }
        }
        
        var volume: Double
            get() = playBin.volume
            set(value) {
                playBin.volume = value
            }
        
        fun play(gstAudioSource: GSTAudioSource) {
            if(currentGSTAudioSource != gstAudioSource)
                playBin.setURI(gstAudioSource.location)
            currentGSTAudioSource = gstAudioSource
            playBin.play()
        }
        
        fun pause() {
            playBin.pause()
        }
        
        fun stop() {
            playBin.stop()
        }
        
        fun seek(position: Duration) {
            playBin.seekSimple(Format.TIME, setOf(SeekFlags.FLUSH, SeekFlags.ACCURATE), (position.toMillis() * 1_000_000.0).toLong())
        }
    }
    
    override var volume: Double
        get() = GSTAudioSource.volume
        set(value) {
            GSTAudioSource.volume = value
        }
    
    override fun init() {
        // TODO load metadata
    }
    
    override fun dispose() {
        // Do nothing
    }
    
    override fun play() = play(this)
    
    override fun pause() = GSTAudioSource.pause()
    
    override fun stop() = GSTAudioSource.stop()
    
    override fun seek(position: Duration) = GSTAudioSource.seek(position)
}