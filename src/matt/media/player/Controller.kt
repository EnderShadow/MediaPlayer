package matt.media.player

import javafx.beans.InvalidationListener
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.Pane
import javafx.scene.media.MediaException
import javafx.scene.media.MediaPlayer
import javafx.scene.paint.Color
import javafx.scene.shape.Arc
import javafx.scene.shape.Line
import javafx.scene.shape.Polygon
import javafx.scene.text.Text
import javafx.stage.*
import matt.media.player.music.NewPlaylistController
import tornadofx.*
import java.io.File
import java.util.*
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
        // TODO shuffling
    }
    
    fun exit()
    {
        Player.stop()
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
        chooser.extensionFilters.clear()
        chooser.extensionFilters.add(FileChooser.ExtensionFilter("Audio Files", supportedAudioFormats.map {"*$it"}))
        chooser.showOpenMultipleDialog(window)?.forEach {
            val uri = it.toURI()
            if(uri !in MediaLibrary.songURIMap)
                try
                {
                    MediaLibrary.addSong(AudioSource(uri))
                }
                catch(me: MediaException)
                {
                    println("Failed to load song. Maybe it's not a song?: ${it.absolutePath}")
                }
        }
    }
    
    fun importMusicFolders()
    {
        val chooser = DirectoryChooser()
        chooser.showDialog(window)?.let {
            it.walkTopDown().asSequence().filter {it.isFile && isSupportedAudioFile(it.name) && it.toURI() !in MediaLibrary.songURIMap}.forEach {
                val uri = it.toURI()
                try
                {
                    MediaLibrary.addSong(AudioSource(uri))
                }
                catch(me: MediaException)
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
}