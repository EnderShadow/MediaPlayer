package matt.media.player

import com.sun.javafx.scene.control.skin.TableViewSkin
import com.sun.javafx.scene.control.skin.VirtualFlow
import javafx.beans.property.StringProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.TableView
import javafx.scene.image.Image
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import javafx.util.Duration
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.net.URI
import java.util.*

var DEBUG = false

val SERIALIZED_MIME_TYPE = DataFormat("application/x-java-serialized-object")

private val imageCache = mutableListOf<Image>()

val defaultImage by lazy {Image(MethodHandles.lookup().lookupClass().classLoader.getResourceAsStream("default.jpg"))}

val validAudioExtensions = listOf(".asf", ".au", ".ogm", ".ogg", ".mka", ".ts", ".mpg", ".mp3", ".mp2", ".nsc", ".nut", ".a52", ".dts",
        ".aac", ".flac", ".dv", ".vid", ".tta", ".tac", ".ty", ".wav", ".dts", ".xa", ".aif", ".aiff", ".m4a")

fun isValidAudioFile(uri: URI) = validAudioExtensions.any {uri.path.endsWith(it, true)}

fun isFile(uri: URI) = uri.isAbsolute && uri.scheme.equals("file", true) || uri.toURL().protocol.equals("file", true)

fun isValidFilename(filename: String): Boolean
{
    return try
    {
        File(filename).canonicalPath
        true
    }
    catch(ioe: IOException)
    {
        false
    }
}

/**
 * runs block until it succeeds or it's tried numAttempts times. If numAttempts is less than or equal to 0 it will run block until it succeeds
 *
 * @return returns the value returned by the block or throws an exception if numAttempts has been exceeded
 */
inline fun <reified T> retry(numAttempts: Int = 0, block: () -> T): T
{
    var numTries = 0
    while(numAttempts <= 0 || numTries < numAttempts)
    {
        try
        {
            return block()
        }
        catch(t: Throwable)
        {
            if(numAttempts >= ++numTries)
                throw t
        }
    }
    throw IllegalStateException("This should never happen")
}

fun formatDuration(duration: Duration?): String
{
    @Suppress("NAME_SHADOWING")
    var duration = duration ?: Duration.ZERO
    if(duration.greaterThanOrEqualTo(Duration.hours(100.0)))
        duration = Duration.seconds((99 * 3600 + 59 * 60 + 59).toDouble())
    val seconds = duration!!.toSeconds().toLong()
    return String.format("%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
}

fun doesAudioSourceMatch(audioSource: AudioSource, filterText: StringProperty): Boolean
{
    return permutations(audioSource.titleProperty.value, audioSource.artistProperty.value, audioSource.albumProperty.value, audioSource.genreProperty.value)
            .any {it.containsSparse(filterText.value, true)}
}

fun permutations(vararg strings: String): Sequence<String>
{
    if(strings.isEmpty())
        return emptySequence()
    if(strings.size == 1)
        return sequenceOf(strings[0])
    
    var seq = emptySequence<String>()
    for(fixed in strings.indices)
        seq += permutations(*strings.slice(strings.indices.filter {it != fixed}).toTypedArray()).map {"${strings[fixed]} $it"}
    return seq
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

fun String.containsSparse(text: String, ignoreCase: Boolean = false): Boolean
{
    if(text.length > length)
        return false
    var index = 0
    for(c in text)
    {
        index = indexOf(c, index, ignoreCase) + 1
        if(index <= 0)
            return false
    }
    return true
}

fun squareAndCache(image: Image): Image
{
    val squaredImage = SwingFXUtils.toFXImage(squareImage(SwingFXUtils.fromFXImage(image, null)), null)
    return squaredImage
    
    // For some reason this doesn't work
    
    val foundImage = imageCache.firstOrNull {it.imageEquals(image)}
    if(foundImage == null)
    {
        imageCache.add(squaredImage)
        return squaredImage
    }
    return foundImage
}

fun Image.imageEquals(other: Image): Boolean
{
    if(width.toInt() != other.width.toInt() || height.toInt() != other.height.toInt())
        return false
    
    val pixelReader = pixelReader
    val oPixelReader = other.pixelReader
    val threshold = 0.02
    for(y in 0 until height.toInt())
        for(x in 0 until width.toInt())
            if(!pixelReader.getColor(x, y).withinThreshold(oPixelReader.getColor(x, y), threshold, threshold, threshold))
                return false
    
    return true
}

fun Color.withinThreshold(other: Color, redThreshold: Double, greenThreshold: Double, blueThreshold: Double): Boolean
{
    if(red - other.red !in (-redThreshold)..redThreshold)
        return false
    if(green - other.green !in (-greenThreshold)..greenThreshold)
        return false
    if(blue - other.blue !in (-blueThreshold)..blueThreshold)
        return false
    return true
}

fun squareImage(bi: BufferedImage): BufferedImage
{
    if(bi.height == bi.width && (Config.maxImageSize <= 0 || Config.maxImageSize >= bi.width))
        return bi
    
    var newSize = Math.min(bi.height, bi.width)
    
    var img: java.awt.Image = bi
    if(Config.maxImageSize in 1 until newSize)
    {
        val scale = Config.maxImageSize.toDouble() / newSize
        img = bi.getScaledInstance(Math.round(bi.width * scale).toInt(), Math.round(bi.height * scale).toInt(), BufferedImage.SCALE_SMOOTH)
        newSize = Config.maxImageSize
    }
    
    val newImage = BufferedImage(newSize, newSize, BufferedImage.TYPE_INT_ARGB)
    val xOffset = (newSize - img.getWidth(null)) / 2
    val yOffset = (newSize - img.getHeight(null)) / 2
    val g = newImage.createGraphics()
    g.drawImage(img, xOffset, yOffset, null)
    g.dispose()
    return newImage
}