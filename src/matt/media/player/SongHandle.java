package matt.media.player;

public class SongHandle
{
	private AudioSource audioSource;
	private SongHandle prev, next;
	
	public SongHandle(AudioSource audioSource, SongHandle prev, SongHandle next)
	{
		this.audioSource = audioSource;
		this.prev = prev;
		if(prev != null)
			prev.next = this;
		this.next = next;
		if(next != null)
			next.prev = this;
	}
	
	public SongHandle(AudioSource audioSource, SongHandle prev)
	{
		this(audioSource, prev, null);
	}
	
	public AudioSource getAudioFile()
	{
		return audioSource;
	}
	
	public SongHandle getNext()
	{
		return next;
	}
	
	public SongHandle getPrev()
	{
		return prev;
	}
	
	public SongHandle setNext(SongHandle newSongHandle)
	{
		SongHandle old = next;
		next = newSongHandle;
		if(next != null)
			next.prev = this;
		if(old != null)
			old.prev = null;
		return old;
	}
	
	public SongHandle setPrev(SongHandle newSongHandle)
	{
		SongHandle old = prev;
		prev = newSongHandle;
		if(prev != null)
			prev.next = this;
		if(old != null)
			old.next = null;
		return old;
	}
}