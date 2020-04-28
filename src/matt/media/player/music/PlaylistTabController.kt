package matt.media.player.music

import javafx.application.Platform
import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.ObjectProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ListChangeListener
import javafx.event.EventType
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.ClipboardContent
import javafx.scene.input.DragEvent
import javafx.scene.input.MouseButton
import javafx.scene.input.TransferMode
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.scene.text.TextAlignment
import javafx.util.Callback
import matt.media.player.*
import org.controlsfx.control.GridCell
import org.controlsfx.control.GridView
import java.util.concurrent.Callable

class PlaylistTabController: TabController()
{
    @FXML private lateinit var playlistView: GridView<PlaylistIcon>
    @FXML private lateinit var stackPane: StackPane
    
    private lateinit var lastClickedCell: GridCell<PlaylistIcon>
    
    private val autoScrollThread = AutoScrollThread()
    
    override fun init()
    {
        playlistView.visibleProperty().bind(Bindings.createBooleanBinding(Callable {stackPane.children.last() == playlistView}, stackPane.children))
        stackPane.children.addListener(InvalidationListener {MediaLibrary.refreshPlaylistIcons()})
        
        val play = MenuItem("Play")
        play.setOnAction {
            Player.clearQueue()
            Player.enqueue((lastClickedCell.graphic as PlaylistIcon).playlist)
            Player.play()
        }
        
        val addToQueue = MenuItem("Add to queue")
        addToQueue.setOnAction {Player.enqueue((lastClickedCell.graphic as PlaylistIcon).playlist)}
        
        val delete = MenuItem("Delete")
        delete.setOnAction {MediaLibrary.removePlaylist((lastClickedCell.graphic as PlaylistIcon).playlist)}
        
        fun createPlaylistFromSelectedPlaylist(addMode: Playlist.PlaylistAddMode)
        {
            val playlist = rootController.requestCreatePlaylist()
            if(playlist != null)
            {
                val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                playlist.addPlaylist(playlistToAdd, addMode)
            }
        }
        
        val byReference = MenuItem("by reference")
        byReference.setOnAction {
            createPlaylistFromSelectedPlaylist(Playlist.PlaylistAddMode.REFERENCE)
        }
        
        val byContents = MenuItem("by contents")
        byContents.setOnAction {
            createPlaylistFromSelectedPlaylist(Playlist.PlaylistAddMode.CONTENTS)
        }
        
        val byFlattened = MenuItem("by flattened")
        byFlattened.setOnAction {
            createPlaylistFromSelectedPlaylist(Playlist.PlaylistAddMode.FLATTENED)
        }
        
        val addToNewPlaylist = Menu("New playlist...", null, byReference, byContents, byFlattened)
        addToNewPlaylist.setOnAction {
            if(it.target != addToNewPlaylist)
                return@setOnAction
            createPlaylistFromSelectedPlaylist(Playlist.PlaylistAddMode.valueOf(Config.getString(ConfigKey.DEFAULT_PLAYLIST_ADD_MODE)))
            var parent = addToNewPlaylist
            while(parent.parentMenu != null)
                parent = parent.parentMenu
            parent.parentPopup.hide()
        }
        
        val addToPlaylist = Menu("Add to playlist", null, addToNewPlaylist, SeparatorMenuItem())
        addToPlaylist.setOnShowing {
            if(MediaLibrary.recentPlaylists.isNotEmpty())
            {
                MediaLibrary.recentPlaylists.forEach {playlist ->
                    @Suppress("NAME_SHADOWING")
                    val byReference = MenuItem("by reference")
                    byReference.setOnAction {_ ->
                        val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                        playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.REFERENCE)
                    }
                    @Suppress("NAME_SHADOWING")
                    val byContents = MenuItem("by contents")
                    byContents.setOnAction {_ ->
                        val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                        playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.CONTENTS)
                    }
                    @Suppress("NAME_SHADOWING")
                    val byFlattened = MenuItem("by flattened")
                    byFlattened.setOnAction {_ ->
                        val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                        playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.FLATTENED)
                    }
                    val playlistMenu = Menu(playlist.name, null, byReference, byContents, byFlattened)
                    playlistMenu.setOnAction {it ->
                        if(it.target != playlistMenu)
                            return@setOnAction
                        val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                        playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.valueOf(Config.getString(ConfigKey.DEFAULT_PLAYLIST_ADD_MODE)))
                        var parent = playlistMenu
                        while(parent.parentMenu != null)
                            parent = parent.parentMenu
                        parent.parentPopup.hide()
                    }
                    addToPlaylist.items.add(playlistMenu)
                }
                addToPlaylist.items.add(SeparatorMenuItem())
            }
            MediaLibrary.playlists.forEach {playlist ->
                @Suppress("NAME_SHADOWING")
                val byReference = MenuItem("by reference")
                byReference.setOnAction {_ ->
                    val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                    playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.REFERENCE)
                }
                @Suppress("NAME_SHADOWING")
                val byContents = MenuItem("by contents")
                byContents.setOnAction {_ ->
                    val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                    playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.CONTENTS)
                }
                @Suppress("NAME_SHADOWING")
                val byFlattened = MenuItem("by flattened")
                byFlattened.setOnAction {_ ->
                    val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                    playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.FLATTENED)
                }
                val playlistMenu = Menu(playlist.name, null, byReference, byContents, byFlattened)
                playlistMenu.setOnAction {it ->
                    if(it.target != playlistMenu)
                        return@setOnAction
                    val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                    playlist.addPlaylist(playlistToAdd, Playlist.PlaylistAddMode.valueOf(Config.getString(ConfigKey.DEFAULT_PLAYLIST_ADD_MODE)))
                    var parent = playlistMenu
                    while(parent.parentMenu != null)
                        parent = parent.parentMenu
                    parent.parentPopup.hide()
                }
                addToPlaylist.items.add(playlistMenu)
            }
        }
        addToPlaylist.setOnHidden {addToPlaylist.items.subList(2, addToPlaylist.items.size).clear()}
        
        val createNewPlaylist = MenuItem("New playlist")
        createNewPlaylist.setOnAction {
            rootController.requestCreatePlaylist()
        }
        
        playlistView.contextMenu = ContextMenu(createNewPlaylist)
        
        val contextMenu = ContextMenu(play, addToQueue, delete, addToPlaylist)
        playlistView.cellWidthProperty().value = stackPane.prefWidth / 5.2 - playlistView.horizontalCellSpacing
        playlistView.cellHeightProperty().bind(playlistView.cellWidthProperty().multiply(1.25))
        playlistView.setCellFactory {
            val cell = object: GridCell<PlaylistIcon>(){
                override fun updateItem(item: PlaylistIcon?, empty: Boolean)
                {
                    super.updateItem(item, empty)
                    graphic = if(empty)
                        null
                    else
                        item
                }
            }
            cell.contextMenu = contextMenu
            cell.setOnMouseClicked {evt ->
                lastClickedCell = cell
                if(evt.button == MouseButton.PRIMARY)
                {
                    val viewer = PlaylistViewer()
                    viewer.prefWidthProperty().bind(stackPane.widthProperty())
                    viewer.prefHeightProperty().bind(stackPane.heightProperty())
                    viewer.backgroundProperty().bind(stackPane.backgroundProperty())
                    val icon = cell.graphic as PlaylistIcon
                    icon.setViewer(viewer)
                    stackPane.children.add(viewer)
                    viewer.visibleProperty().bind(Bindings.createBooleanBinding(Callable {stackPane.children.last() == viewer}, stackPane.children))
                    evt.consume()
                }
                else if(evt.button == MouseButton.SECONDARY)
                {
                    cell.contextMenu.show(rootController.window)
                    evt.consume()
                }
            }
            cell
        }
        
        MediaLibrary.playlists.addListener {listener: ListChangeListener.Change<out Playlist> ->
            while(listener.next())
            {
                if(listener.wasRemoved())
                    MediaLibrary.playlistIcons.removeAt(listener.from)
                else if(listener.wasAdded())
                    MediaLibrary.playlistIcons.add(PlaylistIcon(MediaLibrary.playlists[listener.from]))
            }
        }
        MediaLibrary.playlists.forEach {MediaLibrary.playlistIcons.add(PlaylistIcon(it))}
        
        val filteredList = MediaLibrary.playlistIcons.filtered {it.playlist.name.containsSparse(rootController.filterField.text, true)}
        rootController.filterField.textProperty().addListener {_ -> filteredList.setPredicate {it.playlist.name.containsSparse(rootController.filterField.text, true)}}
        val sortedList = filteredList.sorted()
        sortedList.comparator = Comparator {i1, i2 -> i1.playlist.name.compareTo(i2.playlist.name, true)}
        playlistView.items = sortedList
        playlistView.addEventHandler(EventType.ROOT) {
            //TODO initialize songs in playlist
        }
        
        autoScrollThread.start()
    }
    
    override fun onSelected()
    {
        MediaLibrary.refreshPlaylistIcons()
    }
    
    inner class PlaylistIcon(val playlist: Playlist): VBox(), InvalidationListener
    {
        val nameProperty = SimpleStringProperty()
        private val image1 = ImageView()
        private val image2 = ImageView()
        private val image3 = ImageView()
        private val image4 = ImageView()
        
        init
        {
            playlist.addListener(this)
            nameProperty.bind(playlist.nameProperty)
            
            prefWidthProperty().bind(playlistView.cellWidthProperty())
            prefHeightProperty().bind(playlistView.cellHeightProperty())
            background = Background(BackgroundFill(Color.WHITE, null, null))
            border = Border(BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, null, BorderWidths.DEFAULT, null))
            setupDisplay()
        }
        
        fun setViewer(viewer: PlaylistViewer)
        {
            viewer.playlist = playlist
            (0 until 4).forEach {viewer.images[it].imageProperty().bind(getImage(it))}
            viewer.title.textProperty().bind(nameProperty)
            viewer.numSongs.textProperty().bind(Bindings.createStringBinding(Callable {"${playlist.size()} Songs"}, playlist))
            viewer.duration.textProperty().bind(Bindings.createStringBinding(Callable {formatDuration(playlist.getDuration())}, playlist))
            viewer.mediaListTableView.items = playlist.media
        }
    
        private fun setupDisplay()
        {
            playlist.media.take(10).forEach {
                if(it !is PlaylistHandle || !it.getPlaylist().isRecursivelyEmpty())
                    it.getAudioSource(0).loadImage()
            }
            
            image1.imageProperty().bind(getImage(0))
            image1.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image1.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image1.isSmooth = true
            image1.isCache = true
            image1.isPreserveRatio = true
        
            image2.imageProperty().bind(getImage(1))
            image2.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image2.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image2.isSmooth = true
            image2.isCache = true
            image2.isPreserveRatio = true
        
            image3.imageProperty().bind(getImage(2))
            image3.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image3.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image3.isSmooth = true
            image3.isCache = true
            image3.isPreserveRatio = true
        
            image4.imageProperty().bind(getImage(3))
            image4.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image4.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image4.isSmooth = true
            image4.isCache = true
            image4.isPreserveRatio = true
        
            val imageHBox1 = HBox(image1, image2)
            val imageHBox2 = HBox(image3, image4)
        
            val name = Label()
            name.textProperty().bind(Bindings.createStringBinding(Callable {nameProperty.value.trim()}, nameProperty))
            name.textAlignment = TextAlignment.CENTER
            name.font = Font.font(name.font.family, 16.0)
            name.isWrapText = true
        
            val nameVBox: VBox
            val secondaryText = Label()
            secondaryText.textProperty().set("Playlist")
            secondaryText.visibleProperty().bind(secondaryText.textProperty().isNotEmpty)
            secondaryText.textAlignment = TextAlignment.CENTER
            secondaryText.font = Font.font(secondaryText.font.family, 12.0)
            secondaryText.isWrapText = true
        
            nameVBox = VBox(name, secondaryText)
            nameVBox.alignment = Pos.CENTER
        
            children.clear()
            alignment = Pos.TOP_CENTER
            children.addAll(imageHBox1, imageHBox2, nameVBox)
        }
        
        private fun updateImages()
        {
            playlist.media.take(10).forEach {
                if(it !is PlaylistHandle || !it.getPlaylist().isRecursivelyEmpty())
                    it.getAudioSource(0).loadImage()
            }
            image1.imageProperty().bind(getImage(0))
            image2.imageProperty().bind(getImage(1))
            image3.imageProperty().bind(getImage(2))
            image4.imageProperty().bind(getImage(3))
        }
        
        private fun getImage(index: Int): ObjectProperty<Image>
        {
            // TODO better duplicate removal?
            val images = (0 until playlist.size()).map {playlist.getSong(it).getCurrentAudioSource().imageProperty}.filter {it.value != defaultImage}
                    .distinctBy {it.value}
            return if(images.isEmpty()) SimpleObjectProperty(defaultImage) else images[index % images.size]
        }
    
        override fun invalidated(observable: Observable?) = updateImages()
    }
    
    inner class PlaylistViewer: AnchorPane()
    {
        @FXML lateinit var images: List<ImageView>
            private set
        @FXML lateinit var title: Label
            private set
        @FXML lateinit var numSongs: Label
            private set
        @FXML lateinit var duration: Label
            private set
        @FXML lateinit var mediaListTableView: TableView<MediaHandle>
            private set
    
        lateinit var playlist: Playlist
    
        init
        {
            val loader = FXMLLoader(PlaylistViewer::class.java.getResource("PlaylistViewer.fxml"))
            loader.setRoot(this)
            loader.setController(this)
            loader.load<Any?>()
            stylesheets.add("matt/media/player/music/PlaylistViewer.css")
        
            val play = MenuItem("Play")
            play.setOnAction {
                Player.clearQueue()
                mediaListTableView.selectionModel.selectedItems.forEach(Player::enqueue)
                Player.play()
            }
            
            val addToQueue = MenuItem("Add to queue")
            addToQueue.setOnAction {mediaListTableView.selectionModel.selectedItems.forEach(Player::enqueue)}
        
            val deleteSongs = MenuItem("Delete")
            deleteSongs.setOnAction {mediaListTableView.selectionModel.selectedItems.toList().forEach(playlist::removeMedia)}
    
            val newPlaylist = MenuItem("New playlist...")
            newPlaylist.setOnAction {
                val playlist = rootController.requestCreatePlaylist()
                if(playlist != null)
                    mediaListTableView.selectionModel.selectedItems.forEach {mh ->
                        if(mh is SongHandle)
                            playlist.addSong(mh.getCurrentAudioSource())
                        else
                            playlist.addPlaylist(mh.getPlaylist())
                    }
            }
    
            val addToPlaylist = Menu("Add to playlist", null, newPlaylist, SeparatorMenuItem())
            addToPlaylist.setOnShowing {_ ->
                if(MediaLibrary.recentPlaylists.isNotEmpty())
                {
                    MediaLibrary.recentPlaylists.forEach {playlist ->
                        val playlistMenu = MenuItem(playlist.name)
                        playlistMenu.setOnAction {mediaListTableView.selectionModel.selectedItems.forEach {mh ->
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
                    playlistMenu.setOnAction {mediaListTableView.selectionModel.selectedItems.forEach {mh ->
                        if(mh is SongHandle)
                            playlist.addSong(mh.getCurrentAudioSource())
                        else
                            playlist.addPlaylist(mh.getPlaylist())
                    }}
                    addToPlaylist.items.add(playlistMenu)
                }
            }
            addToPlaylist.setOnHidden {addToPlaylist.items.subList(2, addToPlaylist.items.size).clear()}
    
            val selectionModel = mediaListTableView.selectionModel
            val viewPlaylist = MenuItem("View playlist")
            viewPlaylist.setOnAction {
                val viewer = PlaylistViewer()
                viewer.prefWidthProperty().bind(stackPane.widthProperty())
                viewer.prefHeightProperty().bind(stackPane.heightProperty())
                viewer.backgroundProperty().bind(stackPane.backgroundProperty())
                val icon = MediaLibrary.playlistIcons.first {it.playlist == selectionModel.selectedItem.getPlaylist()}
                icon.setViewer(viewer)
                stackPane.children.add(viewer)
                viewer.visibleProperty().bind(Bindings.createBooleanBinding(Callable {stackPane.children.last() == viewer}, stackPane.children))
                it.consume()
            }
            viewPlaylist.visibleProperty().bind(Bindings.createBooleanBinding(Callable {selectionModel.selectedItems.size == 1 && selectionModel.selectedItem is PlaylistHandle}, selectionModel.selectedItems))
            
            val replaceContents = MenuItem("Contents")
            replaceContents.setOnAction {
                val selectedPlaylist = selectionModel.selectedItem
                val selectedIndex = selectionModel.selectedIndex
                playlist.removeMedia(selectedPlaylist)
                playlist.addPlaylist(selectedIndex, selectedPlaylist.getPlaylist(), Playlist.PlaylistAddMode.CONTENTS)
            }
            val replaceFlattened = MenuItem("Flattened contents")
            replaceFlattened.setOnAction {
                val selectedPlaylist = selectionModel.selectedItem
                val selectedIndex = selectionModel.selectedIndex
                playlist.removeMedia(selectedPlaylist)
                playlist.addPlaylist(selectedIndex, selectedPlaylist.getPlaylist(), Playlist.PlaylistAddMode.FLATTENED)
            }
            val replacePlaylist = Menu("Replace with...", null, replaceContents, replaceFlattened)
            replacePlaylist.visibleProperty().bind(Bindings.createBooleanBinding(Callable {selectionModel.selectedItems.size == 1 && selectionModel.selectedItem is PlaylistHandle}, selectionModel.selectedItems))
            
            val rowFactory = mediaListTableView.rowFactory
            mediaListTableView.setRowFactory {tableView ->
                val row = rowFactory?.call(tableView) ?: TableRow()
                
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
            
            mediaListTableView.addEventFilter(DragEvent.DRAG_OVER) {event ->
                val proximity = mediaListTableView.height / 10
                val tableBounds = mediaListTableView.layoutBounds
                val dragY = event.y
                val topYProximity = tableBounds.minY + proximity
                val bottomYProximity = tableBounds.maxY - proximity
    
                when
                {
                    dragY < topYProximity -> {
                        autoScrollThread.speed = ((topYProximity - dragY) / proximity * 5).toInt() + 1
                        autoScrollThread.scrollMode = ScrollMode.UP
                    }
                    dragY > bottomYProximity -> {
                        autoScrollThread.speed = ((dragY - bottomYProximity) / proximity * 5).toInt() + 1
                        autoScrollThread.scrollMode = ScrollMode.DOWN
                    }
                    else -> autoScrollThread.scrollMode = ScrollMode.NONE
                }
            }
            
            mediaListTableView.addEventFilter(DragEvent.DRAG_DROPPED) {
                autoScrollThread.scrollMode = ScrollMode.NONE
            }
            
            val contextMenu = ContextMenu(play, addToQueue, deleteSongs, addToPlaylist, viewPlaylist, replacePlaylist)
            mediaListTableView.selectionModel.selectionMode = SelectionMode.MULTIPLE
            mediaListTableView.columns.forEach {col ->
                val oldFactory = col.cellFactory
                val cellFactory = Callback<TableColumn<MediaHandle, out Any>, TableCell<MediaHandle, out Any>> {_ ->
                    val cell = oldFactory.call(null)
                    cell.contextMenu = contextMenu
                    cell.setOnMouseClicked {
                        if(it.button == MouseButton.PRIMARY && it.clickCount >= 2 && it.pickResult.intersectedNode != null)
                        {
                            Player.clearQueue()
                            mediaListTableView.selectionModel.selectedItems.forEach(Player::enqueue)
                            Player.play()
                            it.consume()
                        }
                        else if(it.button == MouseButton.SECONDARY)
                        {
                            cell.contextMenu.show(rootController.window)
                            it.consume()
                        }
                    }
                    cell
                }
                col.cellFactory = cellFactory
            }
            
            mediaListTableView.columns.forEach {it.isSortable = false}
            @Suppress("UNCHECKED_CAST")
            val imageColumn = mediaListTableView.columns[0] as TableColumn<MediaHandle, ImageView>
            @Suppress("UNCHECKED_CAST")
            val titleColumn = mediaListTableView.columns[1] as TableColumn<MediaHandle, String>
            @Suppress("UNCHECKED_CAST")
            val durationColumn = mediaListTableView.columns[2] as TableColumn<MediaHandle, String>
            @Suppress("UNCHECKED_CAST")
            val artistColumn = mediaListTableView.columns[3] as TableColumn<MediaHandle, String>
            @Suppress("UNCHECKED_CAST")
            val albumColumn = mediaListTableView.columns[4] as TableColumn<MediaHandle, String>
            
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
            artistColumn.setCellValueFactory {
                if(it.value is SongHandle)
                    it.value.getCurrentAudioSource().artistProperty
                else
                    SimpleStringProperty("")
            }
            albumColumn.setCellValueFactory {
                if(it.value is SongHandle)
                    it.value.getCurrentAudioSource().albumProperty
                else
                    SimpleStringProperty("")
            }
            
            mediaListTableView.addEventHandler(EventType.ROOT) {getVisible(mediaListTableView).forEach {
                if(it !is PlaylistHandle || !it.getPlaylist().isRecursivelyEmpty())
                    it.getAudioSource(0).loadImage()
            }}
        }
        
        fun popViewer() = stackPane.children.remove(this)
        
        fun playPlaylist()
        {
            Player.clearQueue()
            Player.enqueue(playlist)
            Player.play()
        }
    }
    
    private inner class AutoScrollThread: Thread("Playlist autoscroll thread")
    {
        @Volatile
        var scrollMode = ScrollMode.NONE
        @Volatile
        var speed: Int = 0
        
        init
        {
            isDaemon = true
        }
        
        override fun run()
        {
            while(true)
            {
                val scrollBar = stackPane.children.lastOrNull().takeIf {it is PlaylistViewer}?.let {
                    // The scroll bar may be null
                    (it as PlaylistViewer).mediaListTableView.lookup(".scroll-bar:vertical") as ScrollBar?
                }
                
                if(scrollBar != null)
                {
                    when(scrollMode)
                    {
                        ScrollMode.UP -> Platform.runLater {repeat(speed) {scrollBar.decrement()}}
                        ScrollMode.DOWN -> Platform.runLater {repeat(speed) {scrollBar.increment()}}
                        ScrollMode.NONE -> {} // Nothing to do here
                    }
                }
                
                sleep(50)
            }
        }
    }
}