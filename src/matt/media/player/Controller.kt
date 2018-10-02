package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.event.EventType
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.ClipboardContent
import javafx.scene.input.MouseButton
import javafx.scene.input.TransferMode
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.Line
import javafx.scene.shape.Polygon
import javafx.scene.text.Text
import javafx.stage.*
import javafx.util.Callback
import javafx.util.Duration
import matt.media.player.music.NewPlaylistController
import matt.media.player.music.PlaylistTabController
import org.controlsfx.control.PopOver
import java.io.File
import java.lang.IllegalArgumentException
import java.util.concurrent.Callable

class Controller
{
    lateinit var window: Window
    lateinit var settingsWindow: Stage
    
    @FXML private lateinit var fileMenu: Menu
    @FXML private lateinit var helpMenu: Menu
    @FXML private lateinit var splitPane: SplitPane
    @FXML private lateinit var tabPane: TabPane
    @FXML private lateinit var busyIndicator: ProgressIndicator
    @FXML lateinit var filterField: TextField
    @FXML private lateinit var playbackLocationSlider: Slider
    @FXML private lateinit var mediaControlPane: AnchorPane
    @FXML private lateinit var currentlyPlayingText: Text
    @FXML private lateinit var loopSongButton: Button
    @FXML private lateinit var loopIcon1: Arc
    @FXML private lateinit var loopIcon2: Polygon
    @FXML private lateinit var loopSingleIcon: Text
    @FXML private lateinit var previousSongButton: Button
    @FXML private lateinit var playButton: Button
    @FXML private lateinit var playButtonIcon: Polygon
    @FXML private lateinit var pauseButtonIcon: Pane
    @FXML private lateinit var nextSongButton: Button
    @FXML private lateinit var shuffleButton: Button
    @FXML private lateinit var shuffleIcon1: Line
    @FXML private lateinit var shuffleIcon2: Polygon
    @FXML private lateinit var shuffleIcon3: Line
    @FXML private lateinit var shuffleIcon4: Polygon
    @FXML private lateinit var volumeSlider: Slider
    @FXML private lateinit var showQueueButton: Button
    
    private val queuePopOver = PopOver()
    
    fun initialize()
    {
        SplitPane.setResizableWithParent(mediaControlPane, false)
        
        busyIndicator.visibleProperty().bind(MediaLibrary.loadingProperty)
        
        playButtonIcon.visibleProperty().bind(Player.playing.not())
        pauseButtonIcon.visibleProperty().bind(Player.playing)
        playButton.disableProperty().bind(Bindings.createBooleanBinding(Callable {Player.playlistStack[0].isRecursivelyEmpty()}, Player.playlistStack[0]))
        
        val timeChangeListener = InvalidationListener {
            if(!playbackLocationSlider.isValueChanging && Player.currentlyPlaying.value is SongHandle)
                playbackLocationSlider.valueProperty().value = Player.currentlyPlaying.value?.getCurrentAudioSource()?.let {
                    it.currentTimeProperty.value.toMillis() / it.durationProperty.value.toMillis()
                } ?: 0.0
            else
                playbackLocationSlider.valueProperty().value = 0.0
        }
        
        Player.currentlyPlaying.addListener {_, oldValue, newValue ->
            if(oldValue is SongHandle)
                oldValue.getCurrentAudioSource().currentTimeProperty.removeListener(timeChangeListener)
            if(newValue is SongHandle)
                newValue.getCurrentAudioSource().currentTimeProperty.addListener(timeChangeListener)
            timeChangeListener.invalidated(null)
        }
        playbackLocationSlider.disableProperty().bind(Player.currentlyPlaying.isNull)
        playbackLocationSlider.setOnMousePressed {Player.pause()}
        playbackLocationSlider.setOnMouseReleased {
            Player.play()
            Player.currentlyPlaying.value!!.getCurrentAudioSource().run {seek(durationProperty.value.multiply(playbackLocationSlider.value))}
        }
        playbackLocationSlider.valueChangingProperty().addListener(InvalidationListener {
            if(!playbackLocationSlider.isValueChanging)
            {
                Player.pause()
                Player.currentlyPlaying.value!!.getCurrentAudioSource().run {seek(durationProperty.value.multiply(playbackLocationSlider.value))}
                Player.play()
            }
        })
        
        volumeSlider.valueProperty().addListener {_ ->
            Player.volume(volumeSlider.value)
        }
        
        Player.currentlyPlaying.addListener {_, _, newMedia ->
            if(newMedia != null && newMedia is SongHandle)
                currentlyPlayingText.text = "Currently playing: ${newMedia.getCurrentAudioSource().titleProperty.value}"
            else
                currentlyPlayingText.text = "Currently playing:"
        }
        currentlyPlayingText.wrappingWidthProperty().bind(loopSongButton.layoutXProperty().subtract(20))
        
        var colorBinding = Bindings.`when`(Player.loopMode.isEqualTo(LoopMode.NONE)).then(Color.valueOf("BLACK")).otherwise(Color.valueOf("#FF7300"))
        loopIcon1.strokeProperty().bind(colorBinding)
        loopIcon2.strokeProperty().bind(colorBinding)
        loopIcon2.fillProperty().bind(colorBinding)
        loopSingleIcon.visibleProperty().bind(Player.loopMode.isEqualTo(LoopMode.SINGLE))
        
        previousSongButton.disableProperty().bind(Bindings.createBooleanBinding(Callable {Player.playlistStack[0].isRecursivelyEmpty()}, Player.playlistStack[0]))
        nextSongButton.disableProperty().bind(Bindings.createBooleanBinding(Callable {Player.playlistStack[0].isRecursivelyEmpty()}, Player.playlistStack[0]))
        
        colorBinding = Bindings.`when`(Player.shuffling).then(Color.valueOf("#FF7300")).otherwise(Color.valueOf("BLACK"))
        shuffleIcon1.strokeProperty().bind(colorBinding)
        shuffleIcon2.strokeProperty().bind(colorBinding)
        shuffleIcon2.fillProperty().bind(colorBinding)
        shuffleIcon3.strokeProperty().bind(colorBinding)
        shuffleIcon4.strokeProperty().bind(colorBinding)
        shuffleIcon4.fillProperty().bind(colorBinding)
        
        tabPane.selectionModel.selectedItemProperty().addListener {_, _, newValue ->
            (newValue?.content?.userData as TabController?)?.onSelected()
        }
        
        registerTab("music/MusicTab.fxml", "Music")
        registerTab("music/PlaylistTab.fxml", "Playlists")
        //registerTab("music/AlbumTab.fxml", "Albums")
        //registerTab("music/ArtistTab.fxml", "Artists")
        //registerTab("music/GenreTab.fxml", "Genres")
        
        queuePopOver.contentNode = QueueViewer()
        queuePopOver.arrowLocation = PopOver.ArrowLocation.BOTTOM_LEFT
        queuePopOver.fadeInDuration = Duration.millis(100.0)
        queuePopOver.fadeOutDuration = Duration.millis(100.0)
        
        showQueueButton.setOnAction {_ ->
            if(queuePopOver.isShowing)
            {
                queuePopOver.hide()
            }
            else
            {
                queuePopOver.show(showQueueButton, -6.0)
                Player.currentlyPlaying.value?.let {
                    (queuePopOver.contentNode as QueueViewer).scrollTo(it)
                }
            }
        }
    }
    
    fun postInit()
    {
        (queuePopOver.contentNode as QueueViewer).setPrefWidth()
    }
    
    private fun registerTab(fxmlPath: String, tabName: String)
    {
        val loader = FXMLLoader(javaClass.getResource(fxmlPath))
        val tabContent = loader.load<Parent>()
        val controller = loader.getController<TabController>()
        tabContent.userData = controller
        controller.rootController = this
        controller.init()
        val tab = Tab(tabName, tabContent)
        tabPane.tabs.add(tab)
    }
    
    fun playPause()
    {
        if(Player.status == MediaPlayer.Status.PLAYING)
            Player.pause()
        else
            Player.play()
    }
    
    fun toggleLoopMode()
    {
        Player.loopMode.value = when(Player.loopMode.value!!)
        {
            LoopMode.NONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.SINGLE
            LoopMode.SINGLE -> LoopMode.NONE
        }
    }
    
    fun previousSong() = Player.previous()
    
    fun nextSong() = Player.next()
    
    fun shuffle()
    {
        Player.shuffling.value = !Player.shuffling.value
    }
    
    fun exit()
    {
        Player.stop()
        VLCAudioSource.shutdown()
        queuePopOver.hide(Duration.ZERO)
        window.hide()
        val playlistDir = File(Config.mediaDirectory, "Playlists")
        MediaLibrary.playlists.filter {it.dirty}.forEach {it.save(playlistDir)}
    }
    
    fun requestCreatePlaylist(): Playlist?
    {
        val stage = Stage()
        val loader = FXMLLoader(javaClass.getResource("music/NewPlaylist.fxml"))
        val node: Parent = loader.load()
        val controller: NewPlaylistController = loader.getController()
        controller.window = stage
        stage.initOwner(window)
        stage.scene = Scene(node)
        stage.showAndWait()
        
        if(controller.createdPlaylist != null)
            MediaLibrary.addPlaylist(controller.createdPlaylist!!)
        
        return controller.createdPlaylist
    }
    
    fun openSettings()
    {
        settingsWindow.showAndWait()
    }
    
    fun importMusicFiles()
    {
        val chooser = FileChooser()
        chooser.showOpenMultipleDialog(window)?.forEach {
            val uri = it.toURI()
            if(uri !in MediaLibrary.songURIMap)
                try
                {
                    MediaLibrary.addSong(AudioSource.create(uri))
                }
                catch(_: IllegalArgumentException)
                {
                    println("Failed to load song. Maybe it's not a song?: ${it.absolutePath}")
                }
        }
    }
    
    fun importMusicFolders()
    {
        val chooser = DirectoryChooser()
        chooser.showDialog(window)?.let {
            it.walkTopDown().asSequence().filter {it.isFile && it.toURI() !in MediaLibrary.songURIMap}.forEach {
                val uri = it.toURI()
                try
                {
                    MediaLibrary.addSong(AudioSource.create(uri))
                }
                catch(_: IllegalArgumentException)
                {
                    println("Failed to load song. Maybe it's not a song?: ${it.absolutePath}")
                }
            }
        }
    }
    
    fun showAbout()
    {
        // TODO show about
    }
    
    private inner class QueueViewer: TableView<MediaHandle>()
    {
        init
        {
            stylesheets.add("matt/media/player/music/playlistViewer.css")
            
            val imageColumn = TableColumn<MediaHandle, ImageView>("Image")
            imageColumn.isSortable = false
            imageColumn.isEditable = false
            imageColumn.prefWidth = 50.0
            val titleColumn = TableColumn<MediaHandle, String>("Title")
            titleColumn.isSortable = false
            titleColumn.prefWidthProperty().bind(prefWidthProperty().subtract(113))
            val durationColumn = TableColumn<MediaHandle, String>("Duration")
            durationColumn.isSortable = false
            durationColumn.isEditable = false
            durationColumn.prefWidth = 63.0
            
            columns.addAll(imageColumn, titleColumn, durationColumn)
            columns.forEach {col ->
                val oldFactory = col.cellFactory
                val cellFactory = Callback<TableColumn<MediaHandle, out Any>, TableCell<MediaHandle, out Any>> {_ ->
                    val cell = oldFactory.call(null)
                    cell.setOnMouseClicked {
                        if(it.button == MouseButton.PRIMARY && it.clickCount >= 2 && it.pickResult.intersectedNode != null)
                        {
                            val selected = selectionModel.selectedItem
                            Player.jumpTo(selected)
                            it.consume()
                        }
                    }
                    cell
                }
                col.cellFactory = cellFactory
            }
            
            imageColumn.setCellValueFactory {
                val imageView = ImageView()
                imageView.imageProperty().bind(it.value.getCurrentAudioSource().imageProperty)
                imageView.fitWidthProperty().bind(it.tableColumn.widthProperty())
                imageView.isSmooth = true
                imageView.isCache = true
                imageView.isPreserveRatio = true
                SimpleObjectProperty(imageView)
            }
            titleColumn.setCellValueFactory {it.value.getCurrentAudioSource().titleProperty}
            durationColumn.setCellValueFactory {Bindings.createStringBinding(Callable {formatDuration(it.value.getCurrentAudioSource().durationProperty.value)}, it.value.getCurrentAudioSource().durationProperty)}
        
            addEventHandler(EventType.ROOT) {getVisible(this).forEach {it.getCurrentAudioSource().loadImage()}}
            
            items = FXCollections.observableArrayList()
            val flatView = Player.playlistStack[0].flatView()
            flatView.addListener {
                items.clear()
                items.addAll(flatView.songs)
            }
        }
        
        fun setPrefWidth()
        {
            prefWidthProperty().bind(window.widthProperty().multiply(0.3))
            prefHeightProperty().bind(window.heightProperty().multiply(0.7))
        }
    }
}