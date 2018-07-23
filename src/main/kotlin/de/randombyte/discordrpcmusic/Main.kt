package de.randombyte.discordrpcmusic

import be.bluexin.drpc4k.jna.DiscordRichPresence
import be.bluexin.drpc4k.jna.RPCHandler

private const val GOOGLE_PLAY_MUSIC_CHROME_WINDOW_NAME_SUFFIX = " - Google Play Musik - Google Chrome"
private const val XPROP_WINDOW_NAME_PROPERTY_PREFIX = "_NET_WM_NAME(UTF8_STRING) = "
private const val XPROP_WINDOW_IDS_PROPERTY_PREFIX = "_NET_CLIENT_LIST(WINDOW): window id # "

private fun <T> String.execute(lines: (Sequence<String>) -> T): T =
        Runtime.getRuntime().exec(this).inputStream.bufferedReader().useLines(lines)

private class TrackInfo(val title: String, val artist: String) {
    fun buildRichPresenceObject() = DiscordRichPresence {
        details = title
        state = artist
    }
}

/**
 * @return TrackInfo of the currently playing (or paused) track or null if no track or multiple ones could be found
 */
private fun getCurrentTrackInfo(): TrackInfo? {
    val trackInfo = "xprop -root".execute { lines ->
        lines
                .filter { XPROP_WINDOW_IDS_PROPERTY_PREFIX in it }
                .map { it.removePrefix(XPROP_WINDOW_IDS_PROPERTY_PREFIX) }
                .map { it.split(", ") }
                .flatten()
                .mapNotNull { windowId ->
                    "xprop -id $windowId".execute { lines ->
                        lines
                                .filter { XPROP_WINDOW_NAME_PROPERTY_PREFIX in it }
                                .map { it.removePrefix(XPROP_WINDOW_NAME_PROPERTY_PREFIX) }
                                .map { it.removeSurrounding("\"") }
                                .filter { GOOGLE_PLAY_MUSIC_CHROME_WINDOW_NAME_SUFFIX in it }
                                .map { it.removeSuffix(GOOGLE_PLAY_MUSIC_CHROME_WINDOW_NAME_SUFFIX) }
                                .mapNotNull innerMapNotNull@ {
                                    val splits = it.split(" - ", limit = 2)
                                    if (splits.size != 2) return@innerMapNotNull null
                                    TrackInfo(title = splits[0], artist = splits[1])
                                }.singleOrNull()
                    }
                }
                .singleOrNull()
    }

    return trackInfo
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Missing client ID")
        return
    }

    RPCHandler.onErrored = { errorCode, message -> System.err.println("$errorCode = $message") }
    RPCHandler.onDisconnected = { errorCode, message -> println("${if (errorCode != 0) "$errorCode = " else ""}$message") }

    val clientId = args[0]
    RPCHandler.connect(clientId)

    while (true) {
        val trackInfo = getCurrentTrackInfo()
        if (trackInfo == null) {
            System.err.println("Zero or multiple Google Play Music windows found! Only open one.")
        } else {
            RPCHandler.ifConnectedOrLater {
                RPCHandler.updatePresence(trackInfo.buildRichPresenceObject())
            }
        }

        Thread.sleep(5000)
    }

    // maybe use this for a clean shutdown, but nothing behaves in a wrong way currently
    // if (RPCHandler.connected.get()) RPCHandler.disconnect()
    // RPCHandler.finishPending()
}