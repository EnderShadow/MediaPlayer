package matt.media.player;

public abstract class MediaHandle
{
	protected MediaHandle prev, next;
	
	public MediaHandle(MediaHandle prev, MediaHandle next)
	{
		this.prev = prev;
		if(prev != null)
			prev.next = this;
		this.next = next;
		if(next != null)
			next.prev = this;
	}
	
	public MediaHandle(MediaHandle prev)
	{
		this(prev, null);
	}
	
	public abstract AudioSource getCurrentAudioSource();
	public abstract AudioSource getAudioSource(int index);
	public abstract Playlist getPlaylist();
	
	public MediaHandle getNext()
	{
		return next;
	}
	
	public MediaHandle getPrev()
	{
		return prev;
	}
	
	public MediaHandle setNext(MediaHandle newMediaHandle)
	{
		MediaHandle old = next;
		next = newMediaHandle;
		if(next != null)
			next.prev = this;
		if(old != null)
			old.prev = null;
		return old;
	}
	
	public MediaHandle setPrev(MediaHandle newMediaHandle)
	{
		MediaHandle old = prev;
		prev = newMediaHandle;
		if(prev != null)
			prev.next = this;
		if(old != null)
			old.next = null;
		return old;
	}
}