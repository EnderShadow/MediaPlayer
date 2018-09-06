package matt.media.player

import javafx.beans.property.StringProperty
import javafx.scene.control.TableView
import javafx.scene.control.skin.TableViewSkin
import javafx.scene.control.skin.VirtualFlow
import javafx.scene.image.Image
import javafx.util.Duration
import java.lang.invoke.MethodHandles
import java.net.URI
import java.util.ArrayList

var DEBUG = false

fun isFile(uri: URI) = uri.isAbsolute && uri.scheme.equals("file", true) || uri.toURL().protocol.equals("file", true)

val defaultImage by lazy {Image(MethodHandles.lookup().lookupClass().classLoader.getResourceAsStream("default.jpg"))}

fun formatDuration(duration: Duration?): String
{
    @Suppress("NAME_SHADOWING")
    var duration = duration
    if(duration == null)
        duration = Duration.ZERO
    if(duration!!.greaterThanOrEqualTo(Duration.hours(100.0)))
        duration = Duration.seconds((99 * 3600 + 59 * 60 + 59).toDouble())
    val seconds = duration!!.toSeconds().toLong()
    return String.format("%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

fun doesAudioSourceMatch(audioSource: AudioSource, filterText: StringProperty): Boolean
{
    if(audioSource.titleProperty.get().toLowerCase().contains(filterText.get().toLowerCase()))
        return true
    if(audioSource.artistProperty.get().toLowerCase().contains(filterText.get().toLowerCase()))
        return true
    if(audioSource.albumProperty.get().toLowerCase().contains(filterText.get().toLowerCase()))
        return true
    if(audioSource.genreProperty.get().toLowerCase().contains(filterText.get().toLowerCase()))
        return true
    return false
}

fun <T> getVisible(tableView: TableView<T>): List<T>
{
    val retList = ArrayList<T>()
    val skin = tableView.skin as TableViewSkin<*>
    val flow = skin.children.stream().filter {it is VirtualFlow<*>}.findFirst().get() as VirtualFlow<*>
    
    if(flow.firstVisibleCell == null)
        return retList
    val firstIndex = flow.firstVisibleCell.index
    val lastIndex = flow.lastVisibleCell.index
    
    retList.addAll(tableView.items.subList(firstIndex, lastIndex + 1))
    return retList
}