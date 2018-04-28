package matt.media.player

sealed class MediaHandle(private var _prev: MediaHandle?, private var _next: MediaHandle?)
{
    init
    {
        _prev?._next = this
        _next?._prev = this
    }
    
    abstract fun getCurrentAudioSource(): AudioSource
    abstract fun getAudioSource(index: Int): AudioSource
    abstract fun getPlaylist(): Playlist
    
    fun getPrev() = _prev
    fun getNext() = _next
    
    fun setPrev(newMediaHandle: MediaHandle?): MediaHandle?
    {
        val old = _prev
        _prev = newMediaHandle
        _prev?._next = this
        old?._next = null
        return old
    }
    
    fun setNext(newMediaHandle: MediaHandle?): MediaHandle?
    {
        val old = _next
        _next = newMediaHandle
        _next?._prev = this
        old?._prev = null
        return old
    }
}

class SongHandle(private val audioSource: AudioSource, prev: MediaHandle?, next: MediaHandle? = null): MediaHandle(prev, next)
{
    override fun getCurrentAudioSource() = audioSource
    override fun getAudioSource(index: Int) = if(index == 0) audioSource else throw IllegalArgumentException("AudioSource from SongHandle cannot be accessed with non-zero index")
    override fun getPlaylist() = throw UnsupportedOperationException("Cannot get playlist from SongHandle")
}

class PlaylistHandle(private val playlistSource: Playlist, prev: MediaHandle?, next: MediaHandle? = null): MediaHandle(prev, next)
{
    override fun getCurrentAudioSource() = throw UnsupportedOperationException("Cannot get current AudioSource from PlaylistHandle")
    override fun getAudioSource(index: Int) = playlistSource.getSong(index).getCurrentAudioSource()
    override fun getPlaylist() = playlistSource
}