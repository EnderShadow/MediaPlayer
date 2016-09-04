package matt.media.player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.MapChangeListener;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.util.Duration;

public class AudioSource implements Observable
{
	private List<InvalidationListener> listeners = new ArrayList<InvalidationListener>();
	
	private URI uri;
	private MediaPlayer media;
	private Media mediaSource;
	
	private boolean serialized = false;
	
	private InvalidationListener timeChangeListener = obs -> {
		if(!Player.controller.playbackLocationSlider.isValueChanging())
			Player.controller.playbackLocationSlider.valueProperty().set(media.getCurrentTime().toMillis() / media.getTotalDuration().toMillis());
	};
	
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
	
	private AudioSource() {}
	
	public AudioSource(URI location)
	{
		uri = location;
		mediaSource = new Media(uri.toString());
		mediaSource.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
			if(change.getKey().toLowerCase().equals("title") && !((String) change.getValueAdded()).isEmpty())
				title.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("artist"))
				artist.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("album"))
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
		
//		try
//		{
//			AudioFile af = AudioFileIO.read(new File(uri));
//			Tag tag = af.getTagOrCreateDefault();
//			if(tag.hasField(FieldKey.ARTIST) && !tag.getFirst(FieldKey.ARTIST).trim().isEmpty())
//				artist = tag.getFirst(FieldKey.ARTIST);
//			if(tag.hasField(FieldKey.ALBUM) && !tag.getFirst(FieldKey.ALBUM).trim().isEmpty())
//				album = tag.getFirst(FieldKey.ALBUM);
//			if(tag.hasField(FieldKey.TITLE) && !tag.getFirst(FieldKey.TITLE).trim().isEmpty())
//				title = tag.getFirst(FieldKey.TITLE);
//			if(tag.hasField(FieldKey.GENRE) && !tag.getFirst(FieldKey.GENRE).trim().isEmpty())
//				genre = tag.getFirst(FieldKey.GENRE);
//			if(tag.getFirstArtwork() != null)
//				image = SwingFXUtils.toFXImage((BufferedImage) tag.getFirstArtwork().getImage(), null);
//		}
//		catch(Exception e)
//		{
//			e.printStackTrace();
//		}
		if(title.get() == null)
			title.set(uri.getPath().substring(uri.getPath().lastIndexOf("/") + 1, uri.getPath().lastIndexOf(".")));
		
		media.volumeProperty().bind(Player.controller.volumeSlider.valueProperty());
		
		media.setOnEndOfMedia(Player::endOfSongReached);
	}
	
	public AudioSource(String location) throws URISyntaxException
	{
		this(new URI(location));
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
		return mediaSource.durationProperty();
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
		return media != null ? media.getStatus() == Status.PLAYING : false;
	}
	
	public boolean isPaused()
	{
		return media != null ? media.getStatus() == Status.PAUSED : false;
	}
	
	public boolean hasEnded()
	{
		return media != null ? media.getStopTime() == media.getCurrentTime() : false;
	}
	
	public void play()
	{
		media.currentTimeProperty().addListener(timeChangeListener);
		Player.playing.set(true);
		media.play();
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public void pause()
	{
		Player.playing.set(false);
		media.pause();
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public void stop()
	{
		media.currentTimeProperty().removeListener(timeChangeListener);
		Player.playing.set(false);
		seek(0);
		media.stop();
		Player.controller.playbackLocationSlider.setValue(0.0D);
		Util.concurrentForEach(listeners, listener -> listener.invalidated(this));
	}
	
	public void seek(long millis)
	{
		media.seek(Duration.millis(millis));
	}
	
	public URI getURI()
	{
		return uri;
	}
	
	public ReadOnlyObjectProperty<Status> statusProperty()
	{
		return media.statusProperty();
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
	
	public void writeToStream(OutputStream os) throws IOException
	{
		DataOutputStream dos = os instanceof DataOutputStream ? (DataOutputStream) os : new DataOutputStream(os);
		dos.writeUTF(uri.toString());
		dos.writeUTF(title.get());
		dos.writeUTF(album.get());
		dos.writeUTF(artist.get());
		dos.writeUTF(genre.get());
		dos.writeUTF(albumArtist.get());
		byte[] data = Util.toByteArray(Util.prepForCache(SwingFXUtils.fromFXImage(image.get(), null)));
		dos.writeInt(data.length);
		dos.write(data);
		dos.writeInt(playCount.get());
		dos.writeInt(rating.get());
		dos.writeInt(trackCount.get());
		dos.writeInt(trackNumber.get());
		dos.writeInt(year.get());
	}
	
	public static AudioSource readFromStream(InputStream is) throws IOException
	{
		DataInputStream dis = is instanceof DataInputStream ? (DataInputStream) is : new DataInputStream(is);
		AudioSource as = new AudioSource();
		
		try
		{
			as.uri = new URI(dis.readUTF());
		}
		catch(URISyntaxException urise)
		{
			throw new IOException("Bad URI read from stream", urise);
		}
		as.title.set(dis.readUTF());
		as.album.set(dis.readUTF());
		as.artist.set(dis.readUTF());
		as.genre.set(dis.readUTF());
		as.albumArtist.set(dis.readUTF());
		byte[] data = new byte[dis.readInt()];
		dis.readFully(data);
		as.image.set(SwingFXUtils.toFXImage(Util.fromByteArray(data), null));
		as.playCount.set(dis.readInt());
		as.rating.set(dis.readInt());
		as.trackCount.set(dis.readInt());
		as.trackNumber.set(dis.readInt());
		as.year.set(dis.readInt());
		
		as.mediaSource = new Media(as.uri.toString());
		as.mediaSource.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
			if(change.getKey().toLowerCase().equals("title") && !((String) change.getValueAdded()).isEmpty())
				as.title.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("artist"))
				as.artist.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("album"))
				as.album.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("genre"))
				as.genre.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("album artist"))
				as.albumArtist.set((String) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("image"))
				as.image.set(SwingFXUtils.toFXImage(Util.squareImage(SwingFXUtils.fromFXImage((Image) change.getValueAdded(), null)), null));
			else if(change.getKey().toLowerCase().equals("track count"))
				as.trackCount.set((Integer) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("track number"))
				as.trackNumber.set((Integer) change.getValueAdded());
			else if(change.getKey().toLowerCase().equals("year"))
				as.year.set((Integer) change.getValueAdded());
			//System.out.println(change.getKey() + ": " + change.getValueAdded());
		});
		as.media = new MediaPlayer(as.mediaSource);
		as.media.volumeProperty().bind(Player.controller.volumeSlider.valueProperty());
		as.media.setOnEndOfMedia(Player::endOfSongReached);
		as.serialized = true;
		
		return as;
	}
	
	public boolean wasSeriallyLoaded()
	{
		return serialized;
	}
}