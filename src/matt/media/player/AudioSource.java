package matt.media.player;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.util.Duration;

public class AudioSource implements Observable
{
	/**
	 * This should only be accessed by the listener of loadedAudio
	 */
	private static List<AudioSource> backingLoadedAudio = new ArrayList<>(30);
	private static ObservableList<AudioSource> loadedAudio = FXCollections.observableList(backingLoadedAudio);
	
	static
	{
		loadedAudio.addListener((InvalidationListener) obs -> {
			synchronized(loadedAudio)
			{
				String selectedTab = Player.controller.tabPane.getSelectionModel().getSelectedItem().textProperty().get();
				List<AudioSource> temp = Collections.emptyList();
				if(selectedTab.equals("Music") && backingLoadedAudio.size() > 20)
				{
					temp = backingLoadedAudio.stream().filter(as -> !Player.playing.get() || !Player.currentlyPlayingProperty.get().getCurrentAudioSource().equals(as))
							.filter(as -> as.media.getStatus() == Status.READY)
							.filter(as -> !Util.getVisible(Player.controller.musicListTableView).contains(as))
							.limit(backingLoadedAudio.size() - 20).collect(Collectors.toList());
				}
				else if(selectedTab.equals("Albums") && backingLoadedAudio.size() > Util.getVisible(Player.controller.albumListView).size() * 2)
				{
					temp = backingLoadedAudio.stream().filter(as -> !Player.playing.get() || !Player.currentlyPlayingProperty.get().getCurrentAudioSource().equals(as))
							.filter(as -> as.media.getStatus() == Status.READY)
							.filter(as -> Util.getVisible(Player.controller.albumListView).stream().noneMatch(usc -> usc.getVisibleSongs().contains(as)))
							.limit(backingLoadedAudio.size() - Util.getVisible(Player.controller.albumListView).size() * 2).collect(Collectors.toList());
				}
				backingLoadedAudio.removeAll(temp);
				temp.forEach(AudioSource::dispose);
			}
		});
	}
	
	private List<InvalidationListener> listeners = new ArrayList<InvalidationListener>();
	
	private URI uri;
	private MediaPlayer media;
	private Media mediaSource;
	
	private boolean serialized = false;
	private boolean metadataLoaded = false;
	private boolean registered = false;
	
	private InvalidationListener timeChangeListener = obs -> {
		if(!Player.controller.playbackLocationSlider.isValueChanging())
			Player.controller.playbackLocationSlider.valueProperty().set(media.getCurrentTime().toMillis() / media.getTotalDuration().toMillis());
	};
	
	private ObjectProperty<Status> status = new SimpleObjectProperty<>(Status.READY);
	
	private StringProperty title = new SimpleStringProperty(null);
	private StringProperty artist = new SimpleStringProperty("Unknown");
	private StringProperty album = new SimpleStringProperty("Unknown");
	private StringProperty genre = new SimpleStringProperty("");
	private StringProperty albumArtist = new SimpleStringProperty("");
	private ObjectProperty<Image> image = new SimpleObjectProperty<>(Util.getDefaultImage());
	private IntegerProperty playCount = new SimpleIntegerProperty(0);
	private IntegerProperty rating = new SimpleIntegerProperty(0);
	private IntegerProperty trackCount = new SimpleIntegerProperty(0);
	private IntegerProperty trackNumber = new SimpleIntegerProperty(0);
	private IntegerProperty year = new SimpleIntegerProperty(0);
	private ObjectProperty<Duration> duration = new SimpleObjectProperty<>(Duration.ZERO);
	
	private AudioSource() {}
	
	public AudioSource(URI location)
	{
		uri = location;
		
		init(false);
		
		if(title.get() == null)
			title.set(uri.getPath().substring(uri.getPath().lastIndexOf("/") + 1, uri.getPath().lastIndexOf(".")));
	}
	
	public AudioSource(String location) throws URISyntaxException
	{
		this(new URI(location));
	}
	
	public void init()
	{
		init(false);
	}
	
	private void init(boolean forPlayback)
	{
		if(mediaSource == null && (!metadataLoaded || forPlayback))
		{
			mediaSource = new Media(uri.toString());
			mediaSource.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
				if(change.getKey().toLowerCase().equals("title") && !((String) change.getValueAdded()).isEmpty())
					title.set((String) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("artist"))
					artist.set((String) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("album") && !((String) change.getValueAdded()).isEmpty())
					album.set((String) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("genre"))
					genre.set((String) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("album artist"))
					albumArtist.set((String) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("image"))
					image.set(SwingFXUtils.toFXImage(Util.squareImage(SwingFXUtils.fromFXImage((Image) change.getValueAdded(), null)), null));
				else if(change.getKey().toLowerCase().equals("track count"))
					trackCount.set((Integer) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("track number"))
					trackNumber.set((Integer) change.getValueAdded());
				else if(change.getKey().toLowerCase().equals("year"))
					year.set((Integer) change.getValueAdded());
				//System.out.println(change.getKey() + ": " + change.getValueAdded());
			});
			media = new MediaPlayer(mediaSource);
			media.volumeProperty().bind(Player.controller.volumeSlider.valueProperty());
			media.setOnEndOfMedia(Player::endOfSongReached);
			status.bind(media.statusProperty());
			duration.bind(mediaSource.durationProperty());
			metadataLoaded = true;
		}
		if(mediaSource != null)
		{
			synchronized(loadedAudio)
			{
				loadedAudio.remove(this);
				loadedAudio.add(this);
			}
		}
	}
	
	public void dispose()
	{
		if(mediaSource != null)
		{
			synchronized(loadedAudio)
			{
				loadedAudio.remove(this);
			}
			status.unbind();
			status.set(Status.READY);
			duration.unbind();
			mediaSource = null;
			media.volumeProperty().unbind();
			media.dispose();
			media = null;
		}
	}
	
	public MediaPlayer getMediaPlayer()
	{
		return media;
	}
	
	public Media getMediaSource()
	{
		return mediaSource;
	}
	
	public ReadOnlyObjectProperty<Duration> durationProperty()
	{
		return duration;
	}
	
	public StringProperty titleProperty()
	{
		return title;
	}
	
	public void setTitle(String title)
	{
		this.title.set(title == null ? "Unknown" : title);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public StringProperty artistProperty()
	{
		return artist;
	}
	
	public void setArtist(String artist)
	{
		this.artist.set(artist == null ? "Unknown" : artist);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public StringProperty albumProperty()
	{
		return album;
	}
	
	public void setAlbum(String album)
	{
		this.album.set(album == null ? "Unknown" : album);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public StringProperty genreProperty()
	{
		return genre;
	}
	
	public void setGenre(String genre)
	{
		this.genre.set(genre == null ? "Unknown" : genre);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public StringProperty albumArtistProperty()
	{
		if(albumArtist.get().isEmpty())
			albumArtist.set(artist.get());
		return albumArtist;
	}
	
	public void setAlbumArtist(String albumArtist)
	{
		this.albumArtist.set(albumArtist == null ? "" : albumArtist);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public ObjectProperty<Image> imageProperty()
	{
		return image;
	}
	
	public IntegerProperty playCountProperty()
	{
		return playCount;
	}
	
	public IntegerProperty ratingProperty()
	{
		return rating;
	}
	
	public IntegerProperty trackCountProperty()
	{
		return trackCount;
	}
	
	public IntegerProperty trackNumberProperty()
	{
		return trackNumber;
	}
	
	public IntegerProperty yearProperty()
	{
		return year;
	}
	
	public boolean isPlaying()
	{
		return status.get() == Status.PLAYING;
	}
	
	public boolean isPaused()
	{
		return status.get() == Status.PAUSED;
	}
	
	public boolean hasEnded()
	{
		return media != null ? media.getStopTime().equals(media.getCurrentTime()) : false;
	}
	
	public void play()
	{
		if(mediaSource == null)
			init(true);
		if(!registered)
		{
			media.currentTimeProperty().addListener(timeChangeListener);
			registered = true;
		}
		Player.playing.set(true);
		media.play();
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public void pause()
	{
		if(mediaSource == null)
			init(true);
		Player.playing.set(false);
		media.pause();
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public void stop()
	{
		if(mediaSource == null)
			init(true);
		if(registered)
		{
			media.currentTimeProperty().removeListener(timeChangeListener);
			registered = false;
		}
		Player.playing.set(false);
		seek(0);
		media.stop();
		Player.controller.playbackLocationSlider.setValue(0.0D);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public void seek(long millis)
	{
		if(mediaSource == null)
			init(true);
		media.seek(Duration.millis(millis));
	}
	
	public URI getURI()
	{
		return uri;
	}
	
	public ReadOnlyObjectProperty<Status> statusProperty()
	{
		return status;
	}
	
	@Override
	public String toString()
	{
		return uri.toString();
	}
	
	@Override
	public boolean equals(Object other)
	{
		if(!(other instanceof AudioSource))
			return false;
		return uri.equals(((AudioSource) other).uri);
	}
	
	@Override
	public void addListener(InvalidationListener listener)
	{
		listeners.add(listener);
	}
	
	@Override
	public void removeListener(InvalidationListener listener)
	{
		listeners.remove(listener);
	}
	
	public void writeToFile(RandomAccessFile raf) throws IOException
	{
		raf.writeUTF(uri.toString());
		raf.writeUTF(title.get());
		raf.writeUTF(album.get());
		raf.writeUTF(artist.get());
		raf.writeUTF(genre.get());
		raf.writeUTF(albumArtist.get());
		if(Config.imagesInCache && Config.cacheImageSize > 0 && image.get() != Util.getDefaultImage())
		{
			byte[] data = Util.toByteArray(Util.prepForCache(SwingFXUtils.fromFXImage(image.get(), null)));
			raf.writeInt(data.length);
			raf.write(data);
		}
		else
		{
			raf.writeInt(0);
		}
		raf.writeInt(rating.get());
		raf.writeInt(trackCount.get());
		raf.writeInt(trackNumber.get());
		raf.writeInt(year.get());
		raf.writeInt((int) duration.get().toMillis());
	}
	
	public static AudioSource readFromFile(RandomAccessFile raf) throws IOException
	{
		AudioSource as = new AudioSource();
		
		try
		{
			as.uri = new URI(raf.readUTF());
		}
		catch(URISyntaxException urise)
		{
			throw new IOException("Bad URI read from stream", urise);
		}
		as.title.set(raf.readUTF());
		as.album.set(raf.readUTF());
		as.artist.set(raf.readUTF());
		as.genre.set(raf.readUTF());
		as.albumArtist.set(raf.readUTF());
		byte[] data = new byte[raf.readInt()];
		if(data.length > 0)
		{
			if(Config.imagesInCache)
			{
				raf.readFully(data);
				as.image.set(SwingFXUtils.toFXImage(Util.fromByteArray(data), null));
			}
			else
			{
				int amt = 0;
				while(amt < data.length)
					amt += raf.skipBytes(data.length - amt);
				as.image.set(Util.getDefaultImage());
			}
		}
		else
		{
			as.image.set(Util.getDefaultImage());
		}
		as.rating.set(raf.readInt());
		as.trackCount.set(raf.readInt());
		as.trackNumber.set(raf.readInt());
		as.year.set(raf.readInt());
		as.duration.set(Duration.millis(raf.readInt()));
		
		as.serialized = true;
		
		return as;
	}
	
	public boolean wasSeriallyLoaded()
	{
		return serialized;
	}
}