package matt.media.player.music

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
import javafx.scene.input.MouseButton
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
    
    override fun init()
    {
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
    
        val addToNewPlaylist = MenuItem("New playlist...")
        addToNewPlaylist.setOnAction {
            // TODO bring up menu for new playlist and add songs to it
        }
        
        val addToPlaylist = Menu("Add to playlist", null, addToNewPlaylist)
        addToPlaylist.setOnShowing {
            MediaLibrary.playlists.forEach {playlist ->
                val playlistMenu = MenuItem(playlist.name)
                playlistMenu.setOnAction {
                    val playlistToAdd = (lastClickedCell.graphic as PlaylistIcon).playlist
                    // TODO ask how playlistToAdd should be added to playlist
                }
                addToPlaylist.items.add(playlistMenu)
            }
        }
        addToPlaylist.setOnHidden {addToPlaylist.items.subList(1, addToPlaylist.items.size).clear()}
        
        val createNewPlaylist = MenuItem("New playlist")
        createNewPlaylist.setOnAction {
            // TODO create new playlist
        }
        
        playlistView.contextMenu = ContextMenu(createNewPlaylist)
        
        val contextMenu = ContextMenu(play, addToQueue, delete, addToPlaylist)
        playlistView.cellWidthProperty().bind(playlistView.widthProperty().subtract(140).divide(6))
        playlistView.cellHeightProperty().bind(playlistView.cellWidthProperty().multiply(1.25))
        playlistView.setCellFactory {
            val cell = object: GridCell<PlaylistIcon>(){
                override fun updateItem(item: PlaylistIcon?, empty: Boolean)
                {
                    super.updateItem(item, empty)
                    if(item == null || empty)
                        graphic = null
                    else
                        graphic = item
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
                }
                else if(evt.button == MouseButton.SECONDARY)
                {
                    cell.contextMenu.show(rootController.window)
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
        
        val filteredList = MediaLibrary.playlistIcons.filtered {it.playlist.name.contains(rootController.filterField.text, true)}
        rootController.filterField.textProperty().addListener {_ -> filteredList.setPredicate {it.playlist.name.contains(rootController.filterField.text, true)}}
        val sortedList = filteredList.sorted()
        sortedList.comparator = Comparator {i1, i2 -> i1.playlist.name.compareTo(i2.playlist.name, true)}
        playlistView.items = sortedList
        playlistView.addEventHandler(EventType.ROOT) {
            //TODO initialize songs in playlist
        }
    }
    
    override fun onSelected()
    {
        MediaLibrary.refreshPlaylistIcons()
    }
    
    inner class PlaylistIcon(val playlist: Playlist): VBox(), InvalidationListener
    {
        val nameProperty = SimpleStringProperty()
        
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
            val image1 = ImageView()
            image1.imageProperty().bind(getImage(0))
            image1.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image1.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image1.isSmooth = true
            image1.isCache = true
            image1.isPreserveRatio = true
        
            val image2 = ImageView()
            image2.imageProperty().bind(getImage(1))
            image2.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image2.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image2.isSmooth = true
            image2.isCache = true
            image2.isPreserveRatio = true
        
            val image3 = ImageView()
            image3.imageProperty().bind(getImage(2))
            image3.fitWidthProperty().bind(prefWidthProperty().divide(2))
            image3.fitHeightProperty().bind(prefHeightProperty().divide(2))
            image3.isSmooth = true
            image3.isCache = true
            image3.isPreserveRatio = true
        
            val image4 = ImageView()
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
            name.font = Font.font(name.font.family, 18.0)
            name.isWrapText = true
        
            val nameVBox: VBox
            val secondaryText = Label()
            secondaryText.textProperty().set("Playlist")
            secondaryText.visibleProperty().bind(secondaryText.textProperty().isNotEmpty)
            secondaryText.textAlignment = TextAlignment.CENTER
            secondaryText.font = Font.font(secondaryText.font.family, 14.0)
            secondaryText.isWrapText = true
        
            nameVBox = VBox(name, secondaryText)
            nameVBox.alignment = Pos.CENTER
        
            children.clear()
            alignment = Pos.TOP_CENTER
            children.addAll(imageHBox1, imageHBox2, nameVBox)
        }
        
        private fun getImage(index: Int): ObjectProperty<Image>
        {
            // TODO better duplicate removal?
            val images = (0 until playlist.size()).map {playlist.getSong(it).getCurrentAudioSource().imageProperty}.filter {it.value != defaultImage}
                    .distinctBy {it.value}
            return if(images.isEmpty()) SimpleObjectProperty(defaultImage) else images[index % images.size]
        }
    
        override fun invalidated(observable: Observable?) = setupDisplay()
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
        
            val addToQueue = MenuItem("Add to queue")
            addToQueue.setOnAction {mediaListTableView.selectionModel.selectedItems.forEach(Player::enqueue)}
        
            val deleteSongs = MenuItem("Delete")
            deleteSongs.setOnAction {mediaListTableView.selectionModel.selectedItems.toList().forEach(playlist::removeMedia)}
        
            val addToPlaylist = MenuItem("Add to playlist")
            addToPlaylist.setOnAction {
                TODO()
            }
    
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
            }
            viewPlaylist.visibleProperty().bind(Bindings.createBooleanBinding(Callable {selectionModel.selectedItems.size == 1 && selectionModel.selectedItem is PlaylistHandle}, selectionModel.selectedItems))
        
            val contextMenu = ContextMenu(addToQueue, deleteSongs, addToPlaylist, viewPlaylist)
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
                        }
                        else if(it.button == MouseButton.SECONDARY)
                        {
                            cell.contextMenu.show(rootController.window)
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
            
            var visible = emptyList<MediaHandle>()
            mediaListTableView.addEventHandler(EventType.ROOT) {
                val newVisible = getVisible(mediaListTableView)
                for(mediaHandle in visible)
                {
                    if(mediaHandle !in newVisible && Player.currentlyPlaying.value != mediaHandle && mediaHandle is SongHandle)
                        mediaHandle.getCurrentAudioSource().dispose()
                }
                visible = newVisible
                newVisible.filter {it is SongHandle}.forEach {it.getCurrentAudioSource().init()}
            }
        }
        
        fun popViewer() = stackPane.children.remove(this)
    }
}