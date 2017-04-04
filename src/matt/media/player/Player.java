package matt.media.player;

import java.lang.reflect.Method;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.media.MediaPlayer.Status;

public class Player
{
	private static Playlist queue = new Playlist("");
	
	public static ObjectProperty<Playlist> queueProperty = new SimpleObjectProperty<>(queue);
	public static ObjectProperty<MediaHandle> currentlyPlayingProperty = new SimpleObjectProperty<>();
	
	public static Controller controller;
	public static BooleanProperty playing = new SimpleBooleanProperty(false);
	public static ObjectProperty<LoopMode> loopMode = new SimpleObjectProperty<>(LoopMode.NONE);
	
	private static ChangeListener<? super Status> cl = (obs2, oldV2, newV2) -> {
		try
		{
			Method m = ObjectPropertyBase.class.getDeclaredMethod("fireValueChangedEvent");
			m.setAccessible(true);
			m.invoke(currentlyPlayingProperty);
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	};
	
	static
	{
		currentlyPlayingProperty.addListener((obs, oldV, newV) -> {
			if(oldV != newV)
			{
				if(newV != null)
				{
					AudioSource as = newV.getCurrentAudioSource();
					as.statusProperty().addListener(cl);
					as.playCountProperty().set(as.playCountProperty().get() + 1);
				}
				if(oldV != null)
				{
					oldV.getCurrentAudioSource().statusProperty().removeListener(cl);
				}
			}
		});
	}
	
	public static void addToQueue(AudioSource song, boolean playNext)
	{
		if(playNext)
			queue.addSong(queue.indexOf(currentlyPlayingProperty.get()) + 1, song);
		else
			queue.addSong(song);
	}
	
	public static void addToQueue(Playlist playlist, boolean playNext)
	{
		if(playNext)
			queue.addPlaylist(queue.indexOf(currentlyPlayingProperty.get()) + 1, playlist, true, false);
		else
			queue.addPlaylist(playlist);
	}
	
	public static void play()
	{
		MediaHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			if(currentlyPlaying instanceof SongHandle)
			{
				if(currentlyPlaying.getCurrentAudioSource().hasEnded())
				{
					currentlyPlaying.getCurrentAudioSource().stop();
					MediaHandle next = currentlyPlaying.getNext();
					while(next != null && !(next instanceof SongHandle) && (!(next instanceof PlaylistHandle) || next.getPlaylist().isEmpty()))
						next = next.getNext();
					if(next instanceof PlaylistHandle)
						((PlaylistHandle) next).resetIndex();
					currentlyPlayingProperty.setValue(currentlyPlaying = next);
				}
				if(currentlyPlaying != null)
					currentlyPlaying.getCurrentAudioSource().play();
			}
			else if(currentlyPlaying instanceof PlaylistHandle)
			{
				if(currentlyPlaying.getCurrentAudioSource().hasEnded())
				{
					currentlyPlaying.getCurrentAudioSource().stop();
					if(((PlaylistHandle) currentlyPlaying).hasMoreSongs())
					{
						((PlaylistHandle) currentlyPlaying).nextSong();
					}
					else
					{
						MediaHandle next = currentlyPlaying.getNext();
						while(next != null && !(next instanceof SongHandle) && (!(next instanceof PlaylistHandle) || next.getPlaylist().isEmpty()))
							next = next.getNext();
						if(next instanceof PlaylistHandle)
							((PlaylistHandle) next).resetIndex();
						currentlyPlayingProperty.setValue(currentlyPlaying = next);
					}
				}
				if(currentlyPlaying != null)
					currentlyPlaying.getCurrentAudioSource().play();
			}
			else
			{
				System.err.println("Unknown MediaHandle of class " + currentlyPlaying.getClass());
				stop();
			}
		}
		else if(!queue.isEmpty())
		{
			currentlyPlayingProperty.setValue(currentlyPlaying = queue.getSong(0));
			if(currentlyPlaying instanceof PlaylistHandle)
				((PlaylistHandle) currentlyPlaying).resetIndex();
			currentlyPlaying.getCurrentAudioSource().play();
		}
	}
	
	public static void pause()
	{
		MediaHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
			currentlyPlaying.getCurrentAudioSource().pause();
	}
	
	public static void stop()
	{
		MediaHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			currentlyPlaying.getCurrentAudioSource().stop();
			currentlyPlayingProperty.setValue(null);
		}
	}
	
	/**
	 * 
	 * @param playable cannot be null
	 */
	public static void play(MediaHandle playable)
	{
		MediaHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
			currentlyPlaying.getCurrentAudioSource().stop();
		currentlyPlayingProperty.setValue(playable);
		if(playable instanceof PlaylistHandle)
			((PlaylistHandle) playable).resetIndex();
		playable.getCurrentAudioSource().play();
	}
	
	public static void next()
	{
		MediaHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			currentlyPlaying.getCurrentAudioSource().stop();
			if(currentlyPlaying instanceof SongHandle)
			{
				MediaHandle next = currentlyPlaying.getNext();
				while(next != null && !(next instanceof SongHandle) && (!(next instanceof PlaylistHandle) || next.getPlaylist().isEmpty()))
					next = next.getNext();
				if(next instanceof PlaylistHandle)
					((PlaylistHandle) next).resetIndex();
				currentlyPlayingProperty.setValue(currentlyPlaying = next);
			}
			else if(currentlyPlaying instanceof PlaylistHandle)
			{
				if(((PlaylistHandle) currentlyPlaying).hasMoreSongs())
				{
					((PlaylistHandle) currentlyPlaying).nextSong();
				}
				else
				{
					MediaHandle next = currentlyPlaying.getNext();
					while(next != null && !(next instanceof SongHandle) && (!(next instanceof PlaylistHandle) || next.getPlaylist().isEmpty()))
						next = next.getNext();
					if(next instanceof PlaylistHandle)
						((PlaylistHandle) next).resetIndex();
					currentlyPlayingProperty.setValue(currentlyPlaying = next);
				}
			}
			if(currentlyPlaying != null)
				currentlyPlaying.getCurrentAudioSource().play();
			else if(!queue.isEmpty())
				currentlyPlayingProperty.setValue(queue.getSong(0));
		}
	}
	
	public static void previous()
	{
		MediaHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			currentlyPlaying.getCurrentAudioSource().stop();
			if(currentlyPlaying instanceof SongHandle)
			{
				MediaHandle prev = currentlyPlaying.getPrev();
				while(prev != null && !(prev instanceof SongHandle) && (!(prev instanceof PlaylistHandle) || prev.getPlaylist().isEmpty()))
					prev = prev.getPrev();
				if(prev instanceof PlaylistHandle)
					((PlaylistHandle) prev).setIndex(-1);
				currentlyPlayingProperty.setValue(currentlyPlaying = prev);
			}
			else if(currentlyPlaying instanceof PlaylistHandle)
			{
				if(((PlaylistHandle) currentlyPlaying).isAtBeginning())
				{
					((PlaylistHandle) currentlyPlaying).previousSong();
				}
				else
				{
					MediaHandle prev = currentlyPlaying.getPrev();
					while(prev != null && !(prev instanceof SongHandle) && (!(prev instanceof PlaylistHandle) || prev.getPlaylist().isEmpty()))
						prev = prev.getPrev();
					if(prev instanceof PlaylistHandle)
						((PlaylistHandle) prev).setIndex(-1);
					currentlyPlayingProperty.setValue(currentlyPlaying = prev);
				}
			}
			if(currentlyPlaying != null)
				currentlyPlaying.getCurrentAudioSource().play();
			else if(!queue.isEmpty())
				currentlyPlayingProperty.setValue(queue.getSong(0));
		}
	}
	
	public static void clearQueue()
	{
		stop();
		queue.clearPlaylist();
	}
	
	public static void endOfSongReached()
	{
		if(loopMode.get() == LoopMode.SINGLE)
		{
			AudioSource as = currentlyPlayingProperty.get().getCurrentAudioSource();
			as.seek(0);
			as.playCountProperty().set(as.playCountProperty().get() + 1);
		}
		else if(loopMode.get() == LoopMode.ALL && currentlyPlayingProperty.get().getNext() == null)
		{
			play(queue.getSong(0));
		}
		else
		{
			next();
		}
	}
	
	public static Status getStatus()
	{
		if(currentlyPlayingProperty.get() == null)
		{
			return Status.STOPPED;
		}
		else
		{
			return currentlyPlayingProperty.get().getCurrentAudioSource().statusProperty().get();
		}
	}
}