package matt.media.player.music

import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.event.EventType
import javafx.fxml.FXML
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.AnchorPane
import javafx.util.Callback
import matt.media.player.*
import java.util.concurrent.Callable

class MusicTabController: TabController()
{
    @FXML private lateinit var musicListTableView: TableView<AudioSource>
    
    override fun init()
    {
        val addToQueue = MenuItem("Add to queue")
        addToQueue.setOnAction {musicListTableView.selectionModel.selectedItems.forEach(Player::enqueue)}
        
        val deleteSongs = MenuItem("Delete")
        deleteSongs.setOnAction {musicListTableView.selectionModel.selectedItems.toList().forEach(MediaLibrary::removeSong)}
        
        val newPlaylist = MenuItem("New playlist...")
        newPlaylist.setOnAction {
            // TODO bring up menu for new playlist and add songs to it
        }
        
        val addToPlaylist = Menu("Add to playlist", null, newPlaylist)
        addToPlaylist.setOnShowing {_ ->
            MediaLibrary.playlists.forEach {playlist ->
                val playlistMenu = MenuItem(playlist.name)
                playlistMenu.setOnAction {musicListTableView.selectionModel.selectedItems.forEach(playlist::addSong)}
                addToPlaylist.items.add(playlistMenu)
            }
        }
        addToPlaylist.setOnHidden {addToPlaylist.items.subList(1, addToPlaylist.items.size).clear()}
        
        val contextMenu = ContextMenu(addToQueue, deleteSongs, addToPlaylist)
        musicListTableView.selectionModel.selectionMode = SelectionMode.MULTIPLE
        musicListTableView.columns.forEach {col ->
            val oldFactory = col.cellFactory
            val cellFactory = Callback<TableColumn<AudioSource, out Any>, TableCell<AudioSource, out Any>> {_ ->
                val cell = oldFactory.call(null)
                cell.contextMenu = contextMenu
                cell.setOnMouseClicked {
                    if(it.button == MouseButton.PRIMARY && it.clickCount >= 2 && it.pickResult.intersectedNode != null)
                    {
                        Player.clearQueue()
                        Player.enqueue(musicListTableView.selectionModel.selectedItem)
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
        
        @Suppress("UNCHECKED_CAST")
        val imageColumn = musicListTableView.columns[0] as TableColumn<AudioSource, ImageView>
        @Suppress("UNCHECKED_CAST")
        val titleColumn = musicListTableView.columns[1] as TableColumn<AudioSource, String>
        @Suppress("UNCHECKED_CAST")
        val durationColumn = musicListTableView.columns[2] as TableColumn<AudioSource, String>
        @Suppress("UNCHECKED_CAST")
        val artistColumn = musicListTableView.columns[3] as TableColumn<AudioSource, String>
        @Suppress("UNCHECKED_CAST")
        val albumColumn = musicListTableView.columns[4] as TableColumn<AudioSource, String>
        
        imageColumn.setCellValueFactory {
            val imageView = ImageView()
            imageView.imageProperty().bind(it.value.imageProperty)
            imageView.fitWidthProperty().bind(it.tableColumn.widthProperty())
            imageView.isSmooth = true
            imageView.isCache = true
            imageView.isPreserveRatio = true
            SimpleObjectProperty(imageView)
        }
        
        titleColumn.setCellValueFactory {it.value.titleProperty}
        durationColumn.setCellValueFactory {Bindings.createStringBinding(Callable<String> {
            formatDuration(it.value.durationProperty.get())
        }, it.value.durationProperty)}
        artistColumn.setCellValueFactory {it.value.artistProperty}
        albumColumn.setCellValueFactory {it.value.albumProperty}
        
        val filteredList = MediaLibrary.songs.filtered {doesAudioSourceMatch(it, rootController.filterField.textProperty())}
        rootController.filterField.textProperty().addListener {_: Observable -> filteredList.setPredicate {doesAudioSourceMatch(it, rootController.filterField.textProperty())}}
        val sortedList = filteredList.sorted()
        sortedList.comparatorProperty().bind(musicListTableView.comparatorProperty())
        musicListTableView.items = sortedList
        
        musicListTableView.addEventHandler(EventType.ROOT) {getVisible(musicListTableView).forEach {it.loadImage()}}
    }
}