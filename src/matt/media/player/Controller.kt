package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
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
import org.controlsfx.control.PopOver
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
    @FXML private lateinit var currentTime: Text
    @FXML private lateinit var volumeSlider: Slider
    @FXML private lateinit var showQueueButton: Button
    
    private val queuePopOver = PopOver()
    
    fun initialize()
    {
        SplitPane.setResizableWithParent(mediaControlPane, false)
        
        busyIndicator.visibleProperty().bind(MediaLibrary.loadingProperty)
        
        playButtonIcon.visibleProperty().bind(Player.playing.not())
        pauseButtonIcon.visibleProperty().bind(Player.playing)
        playButton.disableProperty().bind(Bindings.createBooleanBinding(Callable {Player.rootQueuePlaylist.isRecursivelyEmpty()}, Player.rootQueuePlaylist))
        
        val timeChangeListener = InvalidationListener {
            if(!playbackLocationSlider.isValueChanging && Player.currentlyPlaying.value is SongHandle)
            {
                playbackLocationSlider.valueProperty().value = Player.currentlyPlaying.value?.getCurrentAudioSource()?.let {
                    it.currentTimeProperty.value.toMillis() / it.durationProperty.value.toMillis()
                } ?: 0.0
                currentTime.text = Player.currentlyPlaying.value?.getCurrentAudioSource()?.let {
                    "${formatDuration(it.currentTimeProperty.value)} / ${formatDuration(it.durationProperty.value)}"
                } ?: "0:00:00 / 0:00:00"
            }
            else
            {
                playbackLocationSlider.valueProperty().value = 0.0
                currentTime.text = "0:00:00 / 0:00:00"
            }
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
            playButtonIcon.requestFocus()
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
        
        previousSongButton.disableProperty().bind(Bindings.createBooleanBinding(Callable {Player.rootQueuePlaylist.isRecursivelyEmpty()}, Player.rootQueuePlaylist))
        nextSongButton.disableProperty().bind(Bindings.createBooleanBinding(Callable {Player.rootQueuePlaylist.isRecursivelyEmpty()}, Player.rootQueuePlaylist))
        
        colorBinding = Bindings.`when`(Player.shuffling).then(Color.valueOf("#FF7300")).otherwise(Color.valueOf("BLACK"))
        shuffleIcon1.strokeProperty().bind(colorBinding)
        shuffleIcon2.strokeProperty().bind(colorBinding)
        shuffleIcon2.fillProperty().bind(colorBinding)
        shuffleIcon3.strokeProperty().bind(colorBinding)
        shuffleIcon4.strokeProperty().bind(colorBinding)
        shuffleIcon4.fillProperty().bind(colorBinding)
        
        tabPane.selectionModel.selectedItemProperty().addListener {_, _, newValue ->
            (newValue?.content?.userData as TabController?)?.onSelected()
            filterField.text = ""
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
                Player.currentlyPlaying.value?.let {selectedItem ->
                    val queueViewer = (queuePopOver.contentNode as QueueViewer)
                    if(queueViewer.tabPane.selectionModel.selectedItem.text.equals("flat view", true))
                    {
                        queueViewer.flatViewTableView.scrollTo(selectedItem as SongHandle)
                    }
                    else
                    {
                        if(selectedItem in Player.rootQueuePlaylist.media)
                        {
                            queueViewer.playlistViewTableView.scrollTo(selectedItem)
                        }
                        else
                        {
                            val item = Player.rootQueuePlaylist.media.asSequence().filter {it is PlaylistHandle}.first {it.getPlaylist().containsRecursive(selectedItem)}
                            queueViewer.playlistViewTableView.scrollTo(item)
                        }
                    }
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
            if(isValidAudioFile(uri) && uri !in MediaLibrary.songs.map {it.location})
                try
                {
                    MediaLibrary.addSong(AudioSource.create(uri))
                }
                catch(_: IllegalArgumentException)
                {
                    println("Failed to load song. Maybe it's not a song?: ${it.absolutePath}")
                }
        }
        MediaLibrary.flushLibrary()
    }
    
    fun importMusicFolders()
    {
        val chooser = DirectoryChooser()
        chooser.showDialog(window)?.let {
            it.walkTopDown().asSequence().filter {it.isFile && isValidAudioFile(it.toURI()) && it.toURI() !in MediaLibrary.songs.map {it.location}}.forEach {
                try
                {
                    MediaLibrary.addSong(AudioSource.create(it.toURI()))
                }
                catch(_: IllegalArgumentException)
                {
                    println("Failed to load song. Maybe it's not a song?: ${it.absolutePath}")
                }
            }
        }
        MediaLibrary.flushLibrary()
    }
    
    fun showAbout()
    {
        // TODO show about
    }
    
    inner class QueueViewer: AnchorPane()
    {
        @FXML lateinit var tabPane: TabPane
        @FXML lateinit var playlistViewTableView: TableView<MediaHandle>
        @FXML lateinit var flatViewTableView: TableView<SongHandle>
        
        init
        {
            val loader = FXMLLoader(QueueViewer::class.java.getResource("QueueViewer.fxml"))
            loader.setRoot(this)
            loader.setController(this)
            loader.load<Any?>()
        }
        
        fun initialize()
        {
            initFlatView()
            initPlaylistView()
        }
        
        private fun initFlatView()
        {
            @Suppress("UNCHECKED_CAST")
            val imageColumn = flatViewTableView.columns[0] as TableColumn<SongHandle, ImageView>
            @Suppress("UNCHECKED_CAST")
            val titleColumn = flatViewTableView.columns[1] as TableColumn<SongHandle, String>
            titleColumn.prefWidthProperty().bind(prefWidthProperty().subtract(135))
            @Suppress("UNCHECKED_CAST")
            val durationColumn = flatViewTableView.columns[2] as TableColumn<SongHandle, String>
    
            flatViewTableView.columns.forEach {col ->
                val oldFactory = col.cellFactory
                val cellFactory = Callback<TableColumn<SongHandle, out Any>, TableCell<SongHandle, out Any>> {_ ->
                    val cell = oldFactory.call(null)
                    cell.setOnMouseClicked {
                        if(it.button == MouseButton.PRIMARY && it.clickCount >= 2 && it.pickResult.intersectedNode != null)
                        {
                            Player.jumpTo(flatViewTableView.selectionModel.selectedIndex)
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
    
            addEventHandler(EventType.ROOT) {getVisible(flatViewTableView).forEach {it.getCurrentAudioSource().loadImage()}}
    
            flatViewTableView.items = FXCollections.observableArrayList()
            Player.flatQueue.addListener {
                flatViewTableView.items.run {
                    clear()
                    addAll(Player.flatQueue.songs)
                }
            }
        }
        
        private fun initPlaylistView()
        {
            playlistViewTableView.stylesheets.add("matt/media/player/music/PlaylistViewer.css")
            
            val playlist = Player.rootQueuePlaylist
            
            val deleteSongs = MenuItem("Delete")
            deleteSongs.setOnAction {
                playlistViewTableView.selectionModel.selectedItems.toList().forEach(playlist::removeMedia)
            }
    
            val newPlaylist = MenuItem("New playlist...")
            newPlaylist.setOnAction {
                val createdPlaylist = requestCreatePlaylist()
                if(createdPlaylist != null)
                    playlistViewTableView.selectionModel.selectedItems.forEach {mh ->
                        if(mh is SongHandle)
                            createdPlaylist.addSong(mh.getCurrentAudioSource())
                        else
                            createdPlaylist.addPlaylist(mh.getPlaylist())
                    }
            }
    
            val addToPlaylist = Menu("Add to playlist", null, newPlaylist, SeparatorMenuItem())
            addToPlaylist.setOnShowing {_ ->
                if(MediaLibrary.recentPlaylists.isNotEmpty())
                {
                    MediaLibrary.recentPlaylists.forEach {playlist ->
                        val playlistMenu = MenuItem(playlist.name)
                        playlistMenu.setOnAction {playlistViewTableView.selectionModel.selectedItems.forEach {mh ->
                            if(mh is SongHandle)
                                playlist.addSong(mh.getCurrentAudioSource())
                            else
                                playlist.addPlaylist(mh.getPlaylist())
                        }}
                        addToPlaylist.items.add(playlistMenu)
                    }
                    addToPlaylist.items.add(SeparatorMenuItem())
                }
                MediaLibrary.playlists.forEach {playlist ->
                    val playlistMenu = MenuItem(playlist.name)
                    playlistMenu.setOnAction {playlistViewTableView.selectionModel.selectedItems.forEach {mh ->
                        if(mh is SongHandle)
                            playlist.addSong(mh.getCurrentAudioSource())
                        else
                            playlist.addPlaylist(mh.getPlaylist())
                    }}
                    addToPlaylist.items.add(playlistMenu)
                }
            }
            addToPlaylist.setOnHidden {addToPlaylist.items.subList(2, addToPlaylist.items.size).clear()}
            
            val selectionModel = playlistViewTableView.selectionModel
            
            val replaceContents = MenuItem("Contents")
            replaceContents.setOnAction {
                val selectedPlaylist = selectionModel.selectedItem
                val selectedIndex = selectionModel.selectedIndex
                
                val paused = Player.status == MediaPlayer.Status.PAUSED
                val time = Player.currentlyPlaying.value?.getCurrentAudioSource()?.currentTimeProperty?.value
                Player.stop(false)
                
                playlist.removeMedia(selectedPlaylist)
                playlist.addPlaylist(selectedIndex, selectedPlaylist.getPlaylist(), Playlist.PlaylistAddMode.CONTENTS)
                
                if(time != null)
                {
                    Player.play()
                    Player.currentlyPlaying.value!!.getCurrentAudioSource().seek(time)
                    if(paused)
                        Player.pause()
                }
            }
            val replaceFlattened = MenuItem("Flattened contents")
            replaceFlattened.setOnAction {
                val selectedPlaylist = selectionModel.selectedItem
                val selectedIndex = selectionModel.selectedIndex
                
                val paused = Player.status == MediaPlayer.Status.PAUSED
                val time = Player.currentlyPlaying.value?.getCurrentAudioSource()?.currentTimeProperty?.value
                Player.stop(false)
                
                playlist.removeMedia(selectedPlaylist)
                playlist.addPlaylist(selectedIndex, selectedPlaylist.getPlaylist(), Playlist.PlaylistAddMode.FLATTENED)
                
                if(time != null)
                {
                    Player.play()
                    Player.currentlyPlaying.value!!.getCurrentAudioSource().seek(time)
                    if(paused)
                        Player.pause()
                }
            }
            val replacePlaylist = Menu("Replace with...", null, replaceContents, replaceFlattened)
            replacePlaylist.visibleProperty().bind(Bindings.createBooleanBinding(Callable {selectionModel.selectedItems.size == 1 && selectionModel.selectedItem is PlaylistHandle}, selectionModel.selectedItems))
            
            playlistViewTableView.setRowFactory {tableView ->
                val row = TableRow<MediaHandle>()
        
                row.setOnDragDetected {
                    if(!row.isEmpty)
                    {
                        val dragBoard = row.startDragAndDrop(TransferMode.MOVE)
                        dragBoard.dragView = row.snapshot(null, null)
                        val clipboard = ClipboardContent()
                        clipboard[SERIALIZED_MIME_TYPE] = tableView.selectionModel.selectedIndices.toList()
                        dragBoard.setContent(clipboard)
                        it.consume()
                    }
                }
        
                row.setOnDragOver {
                    if(it.dragboard.hasContent(SERIALIZED_MIME_TYPE))
                    {
                        val upperHalf = it.y  < row.height / 2.0
                        if(upperHalf)
                        {
                            if("drag-upper" !in row.styleClass)
                                row.styleClass.add("drag-upper")
                            row.styleClass.remove("drag-lower")
                        }
                        else
                        {
                            if("drag-lower" !in row.styleClass)
                                row.styleClass.add("drag-lower")
                            row.styleClass.remove("drag-upper")
                        }
                        it.acceptTransferModes(*TransferMode.COPY_OR_MOVE)
                        it.consume()
                    }
                }
        
                row.setOnDragExited {
                    row.styleClass.remove("drag-upper")
                    row.styleClass.remove("drag-lower")
                    it.consume()
                }
        
                row.setOnDragDropped {event ->
                    val dragboard = event.dragboard
                    if(dragboard.hasContent(SERIALIZED_MIME_TYPE))
                    {
                        @Suppress("UNCHECKED_CAST")
                        val indices = dragboard.getContent(SERIALIZED_MIME_TYPE) as? List<Int>
                        if(indices != null)
                        {
                            val upperHalf = event.y  < row.height / 2.0
                            row.styleClass.remove("drag-upper")
                            row.styleClass.remove("drag-lower")
                            val targetIndex = row.index + if(upperHalf) 0 else 1
                            val media = indices.map {playlist.media[it]}
                            val newIndex = playlist.moveMedia(targetIndex, media)
                            event.isDropCompleted = true
                            tableView.selectionModel.clearSelection()
                            tableView.selectionModel.selectRange(newIndex, newIndex + media.size)
                            event.consume()
                        }
                    }
                }
        
                row
            }
    
            val contextMenu = ContextMenu(deleteSongs, addToPlaylist, replacePlaylist)
            playlistViewTableView.selectionModel.selectionMode = SelectionMode.MULTIPLE
            
            @Suppress("UNCHECKED_CAST")
            val imageColumn = playlistViewTableView.columns[0] as TableColumn<MediaHandle, ImageView>
            @Suppress("UNCHECKED_CAST")
            val titleColumn = playlistViewTableView.columns[1] as TableColumn<MediaHandle, String>
            titleColumn.prefWidthProperty().bind(prefWidthProperty().subtract(135))
            @Suppress("UNCHECKED_CAST")
            val durationColumn = playlistViewTableView.columns[2] as TableColumn<MediaHandle, String>
    
            playlistViewTableView.columns.forEach {col ->
                val oldFactory = col.cellFactory
                val cellFactory = Callback<TableColumn<MediaHandle, out Any>, TableCell<MediaHandle, out Any>> {_ ->
                    val cell = oldFactory.call(null)
                    cell.contextMenu = contextMenu
                    cell.setOnMouseClicked {event ->
                        if(event.button == MouseButton.PRIMARY && event.clickCount >= 2 && event.pickResult.intersectedNode != null)
                        {
                            val selectedIndex = playlistViewTableView.selectionModel.selectedIndex
                            val priorItems = playlistViewTableView.items.subList(0, selectedIndex)
                            val numSongsPrior = priorItems.sumBy {if(it is SongHandle) 1 else it.getPlaylist().size()}
                            Player.jumpTo(numSongsPrior)
                            event.consume()
                        }
                        else if(event.button == MouseButton.SECONDARY)
                        {
                            cell.contextMenu.show(queuePopOver)
                        }
                    }
                    cell
                }
                col.cellFactory = cellFactory
            }
    
            imageColumn.setCellValueFactory {
                val imageView = ImageView()
                if(it.value is PlaylistHandle)
                {
                    if(it.value.getPlaylist().isRecursivelyEmpty())
                        imageView.image = defaultImage
                    else
                        imageView.image = it.value.getPlaylist().getSong(0).getCurrentAudioSource().imageProperty.value
                }
                else
                {
                    imageView.imageProperty().bind(it.value.getCurrentAudioSource().imageProperty)
                }
                imageView.fitWidthProperty().bind(it.tableColumn.widthProperty())
                imageView.isSmooth = true
                imageView.isCache = true
                imageView.isPreserveRatio = true
                SimpleObjectProperty(imageView)
            }
            titleColumn.setCellValueFactory {
                if(it.value is PlaylistHandle)
                    it.value.getPlaylist().nameProperty
                else
                    it.value.getCurrentAudioSource().titleProperty
            }
            durationColumn.setCellValueFactory {
                if(it.value is PlaylistHandle)
                    Bindings.createStringBinding(Callable {formatDuration(it.value.getPlaylist().getDuration())}, it.value.getPlaylist())
                else
                    Bindings.createStringBinding(Callable {formatDuration(it.value.getCurrentAudioSource().durationProperty.value)}, it.value.getCurrentAudioSource().durationProperty)
            }
    
            addEventHandler(EventType.ROOT) {getVisible(playlistViewTableView).forEach {
                if(it !is PlaylistHandle || !it.getPlaylist().isRecursivelyEmpty())
                    it.getAudioSource(0).loadImage()}
            }
    
            playlistViewTableView.items = Player.rootQueuePlaylist.media
        }
        
        fun setPrefWidth()
        {
            prefWidthProperty().bind(window.widthProperty().multiply(0.3))
            prefHeightProperty().bind(window.heightProperty().multiply(0.7))
        }
    }
}