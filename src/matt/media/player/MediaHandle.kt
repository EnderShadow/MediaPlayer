package matt.media.player

sealed class MediaHandle
{
    abstract fun getCurrentAudioSource(): AudioSource
    abstract fun getAudioSource(index: Int): AudioSource
    abstract fun getPlaylist(): Playlist
}

class SongHandle(private val audioSource: AudioSource): MediaHandle()
{
    override fun getCurrentAudioSource() = audioSource
    override fun getAudioSource(index: Int) = if(index == 0) audioSource else throw IllegalArgumentException("AudioSource from SongHandle cannot be accessed with non-zero index")
    override fun getPlaylist() = throw UnsupportedOperationException("Cannot get playlist from SongHandle")
}

class PlaylistHandle(private val playlistSource: Playlist): MediaHandle()
{
    override fun getCurrentAudioSource() = throw UnsupportedOperationException("Cannot get current AudioSource from PlaylistHandle")
    override fun getAudioSource(index: Int) = playlistSource.getSong(index).getCurrentAudioSource()
    override fun getPlaylist() = playlistSource
}