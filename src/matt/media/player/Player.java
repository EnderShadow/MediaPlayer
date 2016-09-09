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
	public static ObjectProperty<SongHandle> currentlyPlayingProperty = new SimpleObjectProperty<>();
	
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
					AudioSource as = newV.getAudioSource();
					as.statusProperty().addListener(cl);
					as.playCountProperty().set(as.playCountProperty().get() + 1);
				}
				if(oldV != null)
				{
					oldV.getAudioSource().statusProperty().removeListener(cl);
				}
			}
		});
	}
	
	public static void addToQueue(AudioSource song)
	{
		queue.addSong(song);
	}
	
	public static void addToQueue(Playlist playlist)
	{
		queue.addPlaylist(playlist);
	}
	
	public static void play()
	{
		SongHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			if(currentlyPlaying.getAudioSource().hasEnded())
			{
				currentlyPlaying.getAudioSource().stop();
				currentlyPlayingProperty.setValue(currentlyPlaying = currentlyPlaying.getNext());
			}
			if(currentlyPlaying != null)
				currentlyPlaying.getAudioSource().play();
		}
		else if(!queue.isEmpty())
		{
			currentlyPlayingProperty.setValue(currentlyPlaying = queue.getSong(0));
			currentlyPlaying.getAudioSource().play();
		}
	}
	
	public static void pause()
	{
		SongHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
			currentlyPlaying.getAudioSource().pause();
	}
	
	public static void stop()
	{
		SongHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			currentlyPlaying.getAudioSource().stop();
			currentlyPlayingProperty.setValue(null);
		}
	}
	
	/**
	 * 
	 * @param playable cannot be null
	 */
	public static void play(SongHandle playable)
	{
		SongHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
			currentlyPlaying.getAudioSource().stop();
		currentlyPlayingProperty.setValue(playable);
		playable.getAudioSource().play();
	}
	
	public static void next()
	{
		SongHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			currentlyPlaying.getAudioSource().stop();
			currentlyPlayingProperty.setValue(currentlyPlaying = currentlyPlaying.getNext());
			if(currentlyPlaying != null)
				currentlyPlaying.getAudioSource().play();
			else if(!queue.isEmpty())
				currentlyPlayingProperty.setValue(queue.getSong(0));
		}
	}
	
	public static void previous()
	{
		SongHandle currentlyPlaying = currentlyPlayingProperty.getValue();
		if(currentlyPlaying != null)
		{
			currentlyPlaying.getAudioSource().stop();
			currentlyPlayingProperty.setValue(currentlyPlaying = currentlyPlaying.getPrev());
			if(currentlyPlaying != null)
				currentlyPlaying.getAudioSource().play();
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
			AudioSource as = currentlyPlayingProperty.get().getAudioSource();
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
			return currentlyPlayingProperty.get().getAudioSource().statusProperty().get();
		}
	}
}