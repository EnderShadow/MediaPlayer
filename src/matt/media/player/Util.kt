package matt.media.player

import java.net.URI

fun isFile(uri: URI) = uri.isAbsolute && uri.scheme.equals("file", true) || uri.toURL().protocol.equals("file", true)