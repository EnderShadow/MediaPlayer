package matt.media.player

import javafx.util.Duration
import org.freedesktop.gstreamer.Gst
import java.io.File
import java.lang.IllegalArgumentException
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class AudioSourceFactory(private val location: URI, private val uuid: UUID = UUID.randomUUID()) {
    companion object {
        private val audioSourceBackers = PriorityQueue<Triple<Int, (URI) -> Boolean, (URI, UUID, LocalDateTime, String, String, String, String, String, Int, Int, String, Duration) -> AudioSource>> {o1, o2 ->
            o1.first.compareTo(o2.first)
        }
        
        init {
            // JavaFX media is always supported
            registerBacker(Int.MAX_VALUE, {JavaFXAudioSource.isSupported(it)}, ::JavaFXAudioSource)
            
            if(VLCAudioSource.vlcDetected())
                registerBacker(0, {VLCAudioSource.isSupported(it)}, ::VLCAudioSource)
            
            if(GSTAudioSource.gstDetected())
                registerBacker(-1, {GSTAudioSource.isSupported(it)}, ::GSTAudioSource)
        }
        
        fun registerBacker(priority: Int, isSupported: (URI) -> Boolean, constructor: (URI, UUID, LocalDateTime, String, String, String, String, String, Int, Int, String, Duration) -> AudioSource) {
            audioSourceBackers.add(Triple(priority, isSupported, constructor))
        }
    }
    
    constructor(audioSource: AudioSource): this(audioSource.location, audioSource.uuid) {
        title = audioSource.titleProperty.value
        artist = audioSource.artistProperty.value
        album = audioSource.albumProperty.value
        genre = audioSource.genreProperty.value
        albumArtist = audioSource.albumArtistProperty.value
        trackCount = audioSource.trackCountProperty.value
        trackNumber = audioSource.trackNumberProperty.value
        year = audioSource.yearProperty.value
        duration = audioSource.durationProperty.value
        dateAdded = audioSource.dateAdded
    }
    
    var title = if(isFile(location)) File(location).nameWithoutExtension else "Unknown"
    var artist = "Unknown"
    var album = "Unknown"
    var genre = ""
    var albumArtist = ""
    var trackCount = 0
    var trackNumber = 0
    var year = ""
    var duration: Duration = Duration.ZERO
    var dateAdded: LocalDateTime = LocalDateTime.now()
    
    fun build(buildNOP: Boolean): AudioSource {
        val backer = if(buildNOP)
            ::NOPAudioSource
        else
            audioSourceBackers.firstOrNull {it.second(location)}?.third ?: throw IllegalArgumentException("Unsupported audio format: $location")
        
        return backer(location, uuid, dateAdded, title, artist, album, genre, albumArtist, trackCount, trackNumber, year, duration)
    }
    
    fun setTitle(newTitle: String): AudioSourceFactory {
        title = newTitle
        return this
    }
    
    fun setArtist(newArtist: String): AudioSourceFactory {
        artist = newArtist
        return this
    }
    
    fun setAlbum(newAlbum: String): AudioSourceFactory {
        album = newAlbum
        return this
    }
    
    fun setGenre(newGenre: String): AudioSourceFactory {
        genre = newGenre
        return this
    }
    
    fun setAlbumArtist(newAlbumArtist: String): AudioSourceFactory {
        albumArtist = newAlbumArtist
        return this
    }
    
    fun setTrackCount(newTrackCount: Int): AudioSourceFactory {
        trackCount = newTrackCount
        return this
    }
    
    fun setTrackNumber(newTrackNumber: Int): AudioSourceFactory {
        trackNumber = newTrackNumber
        return this
    }
    
    fun setYear(newYear: String): AudioSourceFactory {
        year = newYear
        return this
    }
    
    fun setDuration(newDuration: Duration): AudioSourceFactory {
        duration = newDuration
        return this
    }
    
    fun setDateAdded(newDateAdded: LocalDateTime): AudioSourceFactory {
        dateAdded = newDateAdded
        return this
    }
}