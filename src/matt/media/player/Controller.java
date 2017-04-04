package matt.media.player;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Callback;

public class Controller
{
	public Window window;
	public Stage settingsWindow;
	private BooleanProperty shuffling = new SimpleBooleanProperty(false);
	
	@FXML
	private ProgressIndicator busyIndicator;
	private ObjectProperty<AtomicInteger> busyCount = new SimpleObjectProperty<>(new AtomicInteger());
	private Runnable updateIndicator;
	
	{
		try
		{
			Method m = ObjectPropertyBase.class.getDeclaredMethod("fireValueChangedEvent");
			m.setAccessible(true);
			MethodHandle __DO_NOT_USE__ = MethodHandles.lookup().unreflect(m).bindTo(busyCount);
			updateIndicator = () -> {
				try
				{
					__DO_NOT_USE__.invokeExact();
				}
				catch(Throwable t)
				{
					t.printStackTrace();
				}
			};
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
	
	@FXML
	private Menu fileMenu;
	@FXML
	private Menu editMenu;
	
	@FXML
	private Button playButton;
	@FXML
	private Polygon playButtonIcon;
	@FXML
	private Pane pauseButtonIcon;
	
	@FXML
	private SplitPane splitPane;
	
	public TabPane tabPane;
	
	@FXML
	private AnchorPane mediaControlPane;
	@FXML
	private FileChooser fileChooser;
	@FXML
	private DirectoryChooser directoryChooser;
	
	public Slider volumeSlider;
	public Slider playbackLocationSlider;
	
	@FXML
	private Button loopSongButton;
	@FXML
	private Arc loopIcon1;
	@FXML
	private Polygon loopIcon2;
	@FXML
	private Text loopSingleIcon;
	@FXML
	private Button previousSongButton;
	@FXML
	private Button nextSongButton;
	@FXML
	private Button shuffleButton;
	@FXML
	private Line shuffleIcon1;
	@FXML
	private Polygon shuffleIcon2;
	@FXML
	private Line shuffleIcon3;
	@FXML
	private Polygon shuffleIcon4;
	
	@FXML
	private TextField filterField;
	
	public TableView<AudioSource> musicListTableView;
	
	public GridView<UniqueSongCollection> albumListView;
	public GridView<UniqueSongCollection> artistListView;
	public GridView<UniqueSongCollection> genreListView;
	
	private GridCell<UniqueSongCollection> lastClickedCell;
	
	public StackPane albumTab;
	public StackPane artistTab;
	public StackPane genreTab;
	
	public void initialize()
	{
		SplitPane.setResizableWithParent(mediaControlPane, false);
		
		busyIndicator.visibleProperty().bind(Bindings.createBooleanBinding(() -> busyCount.get().get() != 0, busyCount));
		
		fileMenu.disableProperty().bind(busyIndicator.visibleProperty());
		editMenu.disableProperty().bind(busyIndicator.visibleProperty());
		
		playButtonIcon.visibleProperty().bind(Player.playing.not());
		pauseButtonIcon.visibleProperty().bind(Player.playing);
		playButton.disableProperty().bind(Bindings.createBooleanBinding(() -> Player.queueProperty.get().media.isEmpty(), Player.queueProperty.get().media));
		fileChooser = new FileChooser();
		directoryChooser = new DirectoryChooser();
		
		playbackLocationSlider.disableProperty().bind(Player.currentlyPlayingProperty.isNull());
		playbackLocationSlider.setOnMousePressed(evt -> {
			Player.pause();
		});
		playbackLocationSlider.setOnMouseReleased(evt -> {
			Player.currentlyPlayingProperty.get().getCurrentAudioSource().seek((long) Player.currentlyPlayingProperty.get().getCurrentAudioSource().durationProperty().get().multiply(playbackLocationSlider.getValue()).toMillis());
			Player.play();
		});
		playbackLocationSlider.valueChangingProperty().addListener((InvalidationListener) obs -> {
			if(!playbackLocationSlider.isValueChanging())
			{
				Player.pause();
				Player.currentlyPlayingProperty.get().getCurrentAudioSource().seek((long) Player.currentlyPlayingProperty.get().getCurrentAudioSource().durationProperty().get().multiply(playbackLocationSlider.getValue()).toMillis());
				Player.play();
			}
		});
		
		ObjectBinding<Color> colorBinding = Bindings.when(Player.loopMode.isEqualTo(LoopMode.NONE)).then(Color.valueOf("BLACK")).otherwise(Color.valueOf("#FF7300"));
		loopIcon1.strokeProperty().bind(colorBinding);
		loopIcon2.strokeProperty().bind(colorBinding);
		loopIcon2.fillProperty().bind(colorBinding);
		loopSingleIcon.visibleProperty().bind(Player.loopMode.isEqualTo(LoopMode.SINGLE));
		
		previousSongButton.disableProperty().bind(Player.currentlyPlayingProperty.isNull());
		nextSongButton.disableProperty().bind(Player.currentlyPlayingProperty.isNull());
		
		colorBinding = Bindings.when(shuffling).then(Color.valueOf("#FF7300")).otherwise(Color.valueOf("BLACK"));
		shuffleIcon1.strokeProperty().bind(colorBinding);
		shuffleIcon2.strokeProperty().bind(colorBinding);
		shuffleIcon2.fillProperty().bind(colorBinding);
		shuffleIcon3.strokeProperty().bind(colorBinding);
		shuffleIcon4.strokeProperty().bind(colorBinding);
		shuffleIcon4.fillProperty().bind(colorBinding);
		
		Player.controller = this;
		
		setupMusicListTableView();
		setupAlbumListView();
		setupArtistListView();
		setupGenreListView();
	}
	
	public void setupMusicListTableView()
	{
		MenuItem add2queue = new MenuItem("Add to queue");
		add2queue.setOnAction(evt -> {
			musicListTableView.getSelectionModel().getSelectedItems().forEach(as -> Player.addToQueue(as, false));
		});
		
		MenuItem playNext = new MenuItem("Play next");
		playNext.setOnAction(evt -> {
			Util.reversedForEach(musicListTableView.getSelectionModel().getSelectedItems(), as -> Player.addToQueue(as, true));
		});
		
		MenuItem deleteSongs = new MenuItem("Delete");
		deleteSongs.setOnAction(evt -> {
			musicListTableView.getSelectionModel().getSelectedItems().forEach(as -> MediaLibrary.removeSong(as, false));
		});
		
		MenuItem add2playlist = new MenuItem("Add to playlist");
		add2playlist.setOnAction(evt -> {
			// TODO
		});
		
		ContextMenu contextMenu = new ContextMenu(add2queue, playNext, deleteSongs, add2playlist);
		
		musicListTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		for(TableColumn<AudioSource, ?> col : musicListTableView.getColumns())
		{
			@SuppressWarnings("rawtypes")
			Callback temp = col.getCellFactory();
			col.setCellFactory(callback -> {
				@SuppressWarnings("rawtypes")
				TableCell cell = (TableCell) temp.call(callback);
				cell.setContextMenu(contextMenu);
				cell.setOnMouseClicked(evt -> {
					if(evt.getButton().equals(MouseButton.PRIMARY) && evt.getClickCount() >= 2 && evt.getPickResult().getIntersectedNode() != null)
					{
						Player.clearQueue();
						Player.addToQueue(musicListTableView.getSelectionModel().getSelectedItem(), false);
						Player.play();
					}
					else if(evt.getButton().equals(MouseButton.SECONDARY))
					{
						cell.getContextMenu().show(window);
					}
				});
				return cell;
			});
		}
		List<TableColumn<AudioSource, ?>> columns = musicListTableView.getColumns();
		TableColumn<AudioSource, ImageView> imageColumn = (TableColumn<AudioSource, ImageView>) columns.get(0);
		TableColumn<AudioSource, String> titleColumn = (TableColumn<AudioSource, String>) columns.get(1);
		TableColumn<AudioSource, String> durationColumn = (TableColumn<AudioSource, String>) columns.get(2);
		TableColumn<AudioSource, String> artistColumn = (TableColumn<AudioSource, String>) columns.get(3);
		TableColumn<AudioSource, String> albumColumn = (TableColumn<AudioSource, String>) columns.get(4);
		TableColumn<AudioSource, Number> playCountColumn = (TableColumn<AudioSource, Number>) columns.get(5);
		TableColumn<AudioSource, Number> ratingColumn = (TableColumn<AudioSource, Number>) columns.get(6);
		titleColumn.setSortable(true);
		durationColumn.setSortable(true);
		artistColumn.setSortable(true);
		albumColumn.setSortable(true);
		playCountColumn.setSortable(true);
		ratingColumn.setSortable(true);
		
		imageColumn.setCellValueFactory(as -> {
			ImageView iv = new ImageView();
			iv.imageProperty().bind(as.getValue().imageProperty());
			iv.fitWidthProperty().bind(as.getTableColumn().widthProperty());
			iv.setSmooth(true);
			iv.setCache(true);
			iv.setPreserveRatio(true);
			return new SimpleObjectProperty<>(iv);
		});
		titleColumn.setCellValueFactory(as -> as.getValue().titleProperty());
		durationColumn.setCellValueFactory(as -> Bindings.createStringBinding(() -> Util.formatDuration(as.getValue().durationProperty().get()), as.getValue()));
		artistColumn.setCellValueFactory(as -> as.getValue().artistProperty());
		albumColumn.setCellValueFactory(as -> as.getValue().albumProperty());
		playCountColumn.setCellValueFactory(as -> as.getValue().playCountProperty());
		ratingColumn.setCellValueFactory(as -> as.getValue().ratingProperty());
		
		FilteredList<AudioSource> fl = MediaLibrary.songs.filtered(as -> Util.doesAudioSourceMatch(as, filterField.textProperty()));
		filterField.textProperty().addListener(obs -> fl.setPredicate(as -> Util.doesAudioSourceMatch(as, filterField.textProperty())));
		SortedList<AudioSource> sl = fl.sorted();
		sl.comparatorProperty().bind(musicListTableView.comparatorProperty());
		musicListTableView.setItems(sl);
		musicListTableView.addEventHandler(EventType.ROOT, evt -> {
			Util.getVisible(musicListTableView).forEach(AudioSource::init);
		});
	}
	
	public void setupAlbumListView()
	{
		MenuItem play = new MenuItem("Play");
		play.setOnAction(evt -> {
			Player.clearQueue();
			((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList().forEach(as -> Player.addToQueue(as, false));
			Player.play();
		});
		
		MenuItem add2queue = new MenuItem("Add to queue");
		add2queue.setOnAction(evt -> {
			((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList().forEach(as -> Player.addToQueue(as, false));
		});
		
		MenuItem playNext = new MenuItem("Play next");
		playNext.setOnAction(evt -> {
			Util.reversedForEach(((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList(), as -> Player.addToQueue(as, true));
		});
		
		MenuItem deleteSongs = new MenuItem("Delete");
		deleteSongs.setOnAction(evt -> {
			((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList().forEach(as -> MediaLibrary.removeSong(as, false));
		});
		
		MenuItem add2playlist = new MenuItem("Add to playlist");
		add2playlist.setOnAction(evt -> {
			// TODO
		});
		
		ContextMenu contextMenu = new ContextMenu(play, add2queue, playNext, deleteSongs, add2playlist);
		albumListView.cellWidthProperty().bind(albumListView.widthProperty().subtract(140).divide(6));
		albumListView.cellHeightProperty().bind(albumListView.cellWidthProperty().multiply(1.25D));
		albumListView.setCellFactory(list -> {
			GridCell<UniqueSongCollection> cell = new GridCell<UniqueSongCollection>(){
				@Override
				public void updateItem(UniqueSongCollection item, boolean empty)
				{
					super.updateItem(item, empty);
					if(item == null || empty)
						setGraphic(null);
					else
						setGraphic(item);
				}
			};
			cell.setContextMenu(contextMenu);
			cell.setOnMouseClicked(evt -> {
				lastClickedCell = cell;
				if(evt.getButton() == MouseButton.PRIMARY)
				{
					UniqueSongCollectionViewer uscv = new UniqueSongCollectionViewer();
					uscv.prefWidthProperty().bind(albumTab.widthProperty());
					uscv.prefHeightProperty().bind(albumTab.heightProperty());
					uscv.backgroundProperty().bind(splitPane.backgroundProperty());
					UniqueSongCollection usc = (UniqueSongCollection) cell.getGraphic();
					usc.setViewer(uscv);
					albumTab.getChildren().add(uscv);
				}
				else if(evt.getButton() == MouseButton.SECONDARY)
				{
					cell.getContextMenu().show(window);
				}
			});
			return cell;
		});
		FilteredList<UniqueSongCollection> fl = MediaLibrary.albums.filtered(usc -> usc.getUnmodifiableSongList().stream().anyMatch(as -> Util.doesAudioSourceMatch(as, filterField.textProperty())));
		filterField.textProperty().addListener(obs -> fl.setPredicate(usc -> usc.getUnmodifiableSongList().stream().anyMatch(as -> Util.doesAudioSourceMatch(as, filterField.textProperty()))));
		SortedList<UniqueSongCollection> sl = fl.sorted();
		sl.setComparator((usc1, usc2) -> usc1.nameProperty().get().compareToIgnoreCase(usc2.nameProperty().get()));
		albumListView.setItems(sl);
		albumListView.addEventHandler(EventType.ROOT, evt -> {
			Util.getVisible(albumListView).forEach(usc -> usc.getVisibleSongs().forEach(AudioSource::init));
		});
	}
	
	public void setupArtistListView()
	{
		MenuItem shuffle = new MenuItem("Shuffle Artist");
		shuffle.setOnAction(evt -> {
			List<AudioSource> songs = ((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList();
			songs = new ArrayList<>(songs);
			Collections.shuffle(songs);
			Player.clearQueue();
			songs.forEach(as -> Player.addToQueue(as, false));
			Player.play();
		});
		ContextMenu contextMenu = new ContextMenu(shuffle);
		artistListView.cellWidthProperty().bind(artistListView.widthProperty().subtract(140).divide(6));
		artistListView.cellHeightProperty().bind(artistListView.cellWidthProperty().multiply(1.40D));
		artistListView.setCellFactory(list -> {
			GridCell<UniqueSongCollection> cell = new GridCell<UniqueSongCollection>(){
				@Override
				public void updateItem(UniqueSongCollection item, boolean empty)
				{
					super.updateItem(item, empty);
					if(item == null || empty)
						setGraphic(null);
					else
						setGraphic(item);
				}
			};
			cell.setContextMenu(contextMenu);
			cell.setOnMouseClicked(evt -> {
				lastClickedCell = cell;
				if(evt.getButton() == MouseButton.PRIMARY)
				{
					// TODO display albums by artist + album with all songs by artist
					UniqueSongCollectionViewer uscv = new UniqueSongCollectionViewer();
					uscv.prefWidthProperty().bind(artistTab.widthProperty());
					uscv.prefHeightProperty().bind(artistTab.heightProperty());
					uscv.backgroundProperty().bind(splitPane.backgroundProperty());
					UniqueSongCollection usc = (UniqueSongCollection) cell.getGraphic();
					usc.setViewer(uscv);
					artistTab.getChildren().add(uscv);
				}
				else if(evt.getButton() == MouseButton.SECONDARY)
				{
					cell.getContextMenu().show(window);
				}
			});
			return cell;
		});
		FilteredList<UniqueSongCollection> fl = MediaLibrary.artists.filtered(usc -> usc.getUnmodifiableSongList().stream().anyMatch(as -> Util.doesAudioSourceMatch(as, filterField.textProperty())));
		filterField.textProperty().addListener(obs -> fl.setPredicate(usc -> usc.getUnmodifiableSongList().stream().anyMatch(as -> Util.doesAudioSourceMatch(as, filterField.textProperty()))));
		SortedList<UniqueSongCollection> sl = fl.sorted();
		sl.setComparator((usc1, usc2) -> usc1.nameProperty().get().compareToIgnoreCase(usc2.nameProperty().get()));
		artistListView.setItems(sl);
		artistListView.addEventHandler(EventType.ROOT, evt -> {
			Util.getVisible(artistListView).forEach(usc -> usc.getVisibleSongs().forEach(AudioSource::init));
		});
	}
	
	public void setupGenreListView()
	{
		MenuItem play = new MenuItem("Play");
		play.setOnAction(evt -> {
			Player.clearQueue();
			((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList().forEach(as -> Player.addToQueue(as, false));
			Player.play();
		});
		
		MenuItem add2queue = new MenuItem("Add to queue");
		add2queue.setOnAction(evt -> {
			((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList().forEach(as -> Player.addToQueue(as, false));
		});
		
		MenuItem playNext = new MenuItem("Play next");
		playNext.setOnAction(evt -> {
			Util.reversedForEach(((UniqueSongCollection) lastClickedCell.getGraphic()).getUnmodifiableSongList(), as -> Player.addToQueue(as, true));
		});
		
		ContextMenu contextMenu = new ContextMenu(play, add2queue, playNext);
		genreListView.cellWidthProperty().bind(genreListView.widthProperty().subtract(140).divide(6));
		genreListView.cellHeightProperty().bind(genreListView.cellWidthProperty().multiply(1.25D));
		genreListView.setCellFactory(list -> {
			GridCell<UniqueSongCollection> cell = new GridCell<UniqueSongCollection>(){
				@Override
				public void updateItem(UniqueSongCollection item, boolean empty)
				{
					super.updateItem(item, empty);
					if(item == null || empty)
						setGraphic(null);
					else
						setGraphic(item);
				}
			};
			cell.setContextMenu(contextMenu);
			cell.setOnMouseClicked(evt -> {
				lastClickedCell = cell;
				if(evt.getButton() == MouseButton.PRIMARY)
				{
					// TODO display albums of genre + album with all songs of genre
					UniqueSongCollectionViewer uscv = new UniqueSongCollectionViewer();
					uscv.prefWidthProperty().bind(genreTab.widthProperty());
					uscv.prefHeightProperty().bind(genreTab.heightProperty());
					uscv.backgroundProperty().bind(splitPane.backgroundProperty());
					UniqueSongCollection usc = (UniqueSongCollection) cell.getGraphic();
					usc.setViewer(uscv);
					genreTab.getChildren().add(uscv);
				}
				else if(evt.getButton() == MouseButton.SECONDARY)
				{
					cell.getContextMenu().show(window);
				}
			});
			return cell;
		});
		FilteredList<UniqueSongCollection> fl = MediaLibrary.genres.filtered(usc -> usc.getUnmodifiableSongList().stream().anyMatch(as -> Util.doesAudioSourceMatch(as, filterField.textProperty())));
		filterField.textProperty().addListener(obs -> fl.setPredicate(usc -> usc.getUnmodifiableSongList().stream().anyMatch(as -> Util.doesAudioSourceMatch(as, filterField.textProperty()))));
		SortedList<UniqueSongCollection> sl = fl.sorted();
		sl.setComparator((usc1, usc2) -> usc1.nameProperty().get().compareToIgnoreCase(usc2.nameProperty().get()));
		genreListView.setItems(sl);
		genreListView.addEventHandler(EventType.ROOT, evt -> {
			Util.getVisible(genreListView).forEach(usc -> usc.getVisibleSongs().forEach(AudioSource::init));
		});
	}
	
	public void postInit()
	{
		
	}
	
	public void toggle()
	{
		if(Player.getStatus().equals(Status.PLAYING))
			Player.pause();
		else
			Player.play();
	}
	
	public void toggleSongLooping()
	{
		switch(Player.loopMode.get())
		{
		case NONE:
			Player.loopMode.set(LoopMode.ALL);
			break;
		case ALL:
			Player.loopMode.set(LoopMode.SINGLE);
			break;
		case SINGLE:
			Player.loopMode.set(LoopMode.NONE);
			break;
		}
	}
	
	public void prevSong()
	{
		if((Player.playing.get() && Player.currentlyPlayingProperty.get().getCurrentAudioSource().getMediaPlayer().getCurrentTime().toSeconds() > 3.0D) || Player.currentlyPlayingProperty.get().equals(Player.queueProperty.get().getSong(0)))
			Player.currentlyPlayingProperty.get().getCurrentAudioSource().seek(0);
		else
			Player.previous();
	}
	
	public void nextSong()
	{
		Player.next();
	}
	
	public void shuffle()
	{
		shuffling.set(!shuffling.get());
		Player.queueProperty.get().shuffle(shuffling.get());
	}
	
	public void showMusicFileFinder()
	{
		List<File> temp = fileChooser.showOpenMultipleDialog(window);
		if(temp != null && temp.size() > 0)
		{
			addMusicFiles(new ArrayList<>(temp));
		}
	}
	
	public void addMusicFiles(List<File> files)
	{
		if(files != null && files.size() > 0)
		{
			files.removeIf(Util::isUnsupportedAudio);
			
			Task<Void> task = new Task<Void>()
			{
				@Override
				public Void call()
				{
					updateProgress(0, files.size());
					updateMessage("0/" + files.size());
					for(int i = 0; i < files.size(); i++)
					{
						if(isCancelled())
							return null;
						File file = files.get(i);
						String artist = "Unknown";
						String album = "Unknown";
						String title = file.getName();
						int extOffset = title.lastIndexOf(".");
						String extension = title.substring(extOffset);
						title = title.substring(0, extOffset);
						try
						{
							AudioFile af = AudioFileIO.read(file);
							Tag tag = af.getTagOrCreateDefault();
							if(tag.hasField(FieldKey.ARTIST) && !tag.getFirst(FieldKey.ARTIST).trim().isEmpty())
								artist = tag.getFirst(FieldKey.ARTIST);
							if(tag.hasField(FieldKey.ALBUM) && !tag.getFirst(FieldKey.ALBUM).trim().isEmpty())
								album = tag.getFirst(FieldKey.ALBUM);
							if(tag.hasField(FieldKey.TITLE) && !tag.getFirst(FieldKey.TITLE).trim().isEmpty())
								title = tag.getFirst(FieldKey.TITLE);
						}
						catch(Exception e)
						{
							e.printStackTrace();
						}
						File newLoc = new File(Config.mediaDirectory, Util.cleanFileName(artist) + File.separator + Util.cleanFileName(album) + File.separator + Util.cleanFileName(title) + extension);
						File dir = newLoc.getParentFile();
						if(!dir.exists())
							dir.mkdirs();
						try
						{
							if(newLoc.exists())
							{
								AudioSource as2 = MediaLibrary.urlSongMap.remove(newLoc.toURI());
								if(as2 != null)
								{
									as2.dispose();
									MediaLibrary.songs.remove(as2);
								}
							}
							Files.copy(file.toPath(), newLoc.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
							AudioSource temp = new AudioSource(newLoc.toURI());
							//MediaLibrary.addSong(temp);
							//updateProgress(i + 1, files.size());
							//updateMessage(i + 1 + "/" + files.size());
							
							// for when media is instantiated on load
							final int j = i;
							temp.getMediaPlayer().setOnReady(() -> {
								if(Config.unloadInvisibleSongs)
									temp.dispose();
								MediaLibrary.addSong(temp);
								updateProgress(j + 1, files.size());
								updateMessage(j + 1 + "/" + files.size());
							});
						}
						catch(IOException ioe)
						{
							ioe.printStackTrace();
							System.err.println("Failed to copy song from " + file.getAbsolutePath() + " to " + newLoc.getAbsolutePath() + "\nRetrying later");
							files.add(files.remove(i));
							i--;
						}
					}
					
					if(Config.unloadInvisibleSongs)
					{
						System.gc();
						Runtime.getRuntime().freeMemory();
					}
					
					MediaLibrary.cacheAll();
					
					return null;
				}
			};
			
			FileTransferDisplay ftd = new FileTransferDisplay("Importing songs", task.progressProperty(), task.messageProperty());
			
			task.setOnSucceeded(evt -> ftd.hide());
			
			task.setOnFailed(evt -> {
				task.getException().printStackTrace();
				System.exit(-1);
			});
			
			ftd.display();
			
			Thread t = new Thread(task);
			t.start();
		}
	}
	
	public void showMusicFolderFinder()
	{
		File dir = directoryChooser.showDialog(window);
		if(dir != null)
		{
			LinkedList<File> toWalk = new LinkedList<File>();
			LinkedList<File> toAdd = new LinkedList<File>();
			toWalk.add(dir);
			while(toWalk.size() > 0)
			{
				dir = toWalk.removeFirst();
				for(File file : dir.listFiles())
				{
					if(file.isDirectory())
						toWalk.addLast(file);
					else
						toAdd.add(file);
				}
			}
			addMusicFiles(toAdd);
		}
	}
	
	public Task<Void> loadMusic()
	{
		return new Task<Void>()
		{
			@Override
			public Void call()
			{
				busyCount.get().incrementAndGet();
				updateIndicator.run();
				
				File dir = Config.mediaDirectory;
				int numFiles = Util.countFiles(dir);
				this.updateProgress(0, numFiles);
				this.updateMessage("0/" + numFiles);
				
				int curFile = Cache.retrieveAll(0, numFiles, this::updateProgress, this::updateMessage);
				
				LinkedList<File> toWalk = new LinkedList<File>();
				toWalk.add(dir);
				tempLbl: while(toWalk.size() > 0)
				{
					dir = toWalk.removeFirst();
					errLbl: for(File f : dir.listFiles())
					{
						if(isCancelled())
						{
							busyCount.get().decrementAndGet();
							updateIndicator.run();
							return null;
						}
						
						if(f.isDirectory())
						{
							toWalk.addLast(f);
						}
						else if(!Util.isUnsupportedAudio(f) && !MediaLibrary.urlSongMap.containsKey(f.toURI()))
						{
							curFile++;
							AudioSource temp = new AudioSource(f.toURI());
							while(temp.statusProperty().get() != Status.READY && temp.getMediaPlayer().getError() == null)
							{
								Thread.yield();
							}
							if(temp.getMediaPlayer().getError() != null)
							{
								System.err.println("Failed to load " + f.toURI());
								temp.getMediaPlayer().getError().printStackTrace();
								continue errLbl;
							}
							if(Config.unloadInvisibleSongs)
								temp.dispose();
							MediaLibrary.addSong(temp);
							this.updateProgress(curFile, numFiles);
							this.updateMessage(curFile + "/" + numFiles);
							if(Config.unloadInvisibleSongs && curFile % 10 == 0)
								System.gc();
							//if(MediaLibrary.songs.size() > 50)
							//	break tempLbl;
						}
					}
				}
				
				if(Config.unloadInvisibleSongs)
				{
					System.gc();
					Runtime.getRuntime().freeMemory();
				}
				
				busyCount.get().decrementAndGet();
				updateIndicator.run();
				
				if(Cache.needsRebuilding)
				{
					MediaLibrary.forgetToCache();
					Cache.reset();
				}
				else
				{
					MediaLibrary.cacheAll();
				}
				
				// load playlists
				MediaLibrary.loadPlaylists();
				
				return null;
			}
		};
	}
	
	public void showSettings()
	{
		settingsWindow.showAndWait();
	}
	
	public void exit()
	{
		Player.stop();
		window.hide();
		
		// cleanup code
		File playlistDir = new File(Config.mediaDirectory, "Playlists");
		for(Playlist p : MediaLibrary.playlists)
			if(p.isDirty())
				p.save(playlistDir);
	}
}