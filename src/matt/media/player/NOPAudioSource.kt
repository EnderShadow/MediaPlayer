package matt.media.player

import javafx.util.Duration
import java.net.URI
import java.time.LocalDateTime
import java.util.*

class NOPAudioSource(location: URI, uuid: UUID, dateAdded: LocalDateTime, title: String, artist: String, album: String, genre: String, albumArtist: String, trackCount: Int, trackNumber: Int, year: String, duration: Duration): AudioSource(location, uuid, dateAdded, title, artist, album, genre, albumArtist, trackCount, trackNumber, year, duration)
{
    override var volume: Double
        get() = Player.volume
        set(_) {}
    
    override fun init() {}
    override fun dispose() {}
    override fun play() {}
    override fun pause() {}
    override fun stop() {}
    override fun seek(position: Duration) {}
}