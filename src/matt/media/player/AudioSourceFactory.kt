package matt.media.player

import javafx.util.Duration
import java.io.File
import java.lang.IllegalArgumentException
import java.net.URI
import java.util.*

class AudioSourceFactory(private val location: URI, private val uuid: UUID = UUID.randomUUID()) {
    companion object {
        private val audioSourceBackers = PriorityQueue<Triple<Int, (URI) -> Boolean, (URI, UUID, String, String, String, String, String, Int, Int, String, Duration) -> AudioSource>> {o1, o2 ->
            o1.first.compareTo(o2.first)
        }
        
        init {
            // JavaFX media is always supported
            registerBacker(Int.MAX_VALUE, {JavaFXAudioSource.isSupported(it)}, ::JavaFXAudioSource)
            
            if(VLCAudioSource.vlcDetected())
                registerBacker(0, {VLCAudioSource.isSupported(it)}, ::VLCAudioSource)
        }
        
        fun registerBacker(priority: Int, isSupported: (URI) -> Boolean, constructor: (URI, UUID, String, String, String, String, String, Int, Int, String, Duration) -> AudioSource) {
            audioSourceBackers.add(Triple(priority, isSupported, constructor))
        }
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
    
    fun build(): AudioSource {
        val backer = audioSourceBackers.firstOrNull {it.second(location)}?.third ?: throw IllegalArgumentException("Unsupported audio format: $location")
        return backer(location, uuid, title, artist, album, genre, albumArtist, trackCount, trackNumber, year, duration)
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
}