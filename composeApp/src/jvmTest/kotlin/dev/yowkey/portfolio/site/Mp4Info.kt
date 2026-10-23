package dev.yowkey.portfolio.site

/**
 * What the site needs to know about a clip's `.mp4`, read from its boxes (ISO/IEC 14496-12) without a decoder:
 * the video track's codec, size and frame rate, whether there is a sound track, and whether the index (`moov`)
 * comes before the frames (`mdat`), which lets a browser start playing before the whole file has arrived.
 */
data class Mp4Info(
    val codec: String,
    val width: Int,
    val height: Int,
    val frames: Int,
    val seconds: Double,
    val hasAudio: Boolean,
    val indexFirst: Boolean,
) {
    val framesPerSecond: Double get() = frames / seconds

    companion object {
        /** Throws [IllegalArgumentException] when [bytes] is not an mp4 with exactly one video track. */
        fun read(bytes: ByteArray): Mp4Info {
            val top = boxes(bytes, 0, bytes.size)
            require(top.firstOrNull()?.type == "ftyp") { "not an mp4: it does not start with an ftyp box" }
            val moov = requireNotNull(top.firstOrNull { it.type == "moov" }) { "no moov box" }
            val mdat = top.indexOfFirst { it.type == "mdat" }
            val tracks = boxes(bytes, moov.start, moov.end).filter { it.type == "trak" }.map { track(bytes, it) }
            val video = tracks.filter { it.handler == "vide" }
            require(video.size == 1) { "expected one video track, found ${video.size}" }
            return with(video.single()) {
                Mp4Info(codec, width, height, frames, seconds, tracks.any { it.handler == "soun" }, top.indexOf(moov) < mdat)
            }
        }
    }
}

/** One box: its four-letter type, and where its content starts and ends in the file. */
private class Box(val type: String, val start: Int, val end: Int)

private class Track(val handler: String, val codec: String, val width: Int, val height: Int, val frames: Int, val seconds: Double)

private fun boxes(bytes: ByteArray, from: Int, to: Int): List<Box> = buildList {
    var at = from
    while (at + 8 <= to) {
        var size = u32(bytes, at)
        val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
        var header = 8
        when (size) {
            1L -> { size = u64(bytes, at + 8); header = 16 } // a 64-bit size follows the type
            0L -> size = (to - at).toLong() // the box runs to the end of its parent
        }
        require(size >= header && at + size <= to) { "box \"$type\" at byte $at has a bad size, $size" }
        add(Box(type, at + header, (at + size).toInt()))
        at += size.toInt()
    }
}

private fun child(bytes: ByteArray, parent: Box, type: String): Box =
    requireNotNull(boxes(bytes, parent.start, parent.end).firstOrNull { it.type == type }) { "no $type box in ${parent.type}" }

private fun track(bytes: ByteArray, trak: Box): Track {
    val mdia = child(bytes, trak, "mdia")
    val mdhd = child(bytes, mdia, "mdhd")
    // Version 1 widens the creation and modification times from 4 bytes to 8.
    val timescale = u32(bytes, mdhd.start + if (bytes[mdhd.start].toInt() == 1) 20 else 12)
    val handler = String(bytes, child(bytes, mdia, "hdlr").start + 8, 4, Charsets.US_ASCII)
    val stbl = child(bytes, child(bytes, mdia, "minf"), "stbl")
    // The first sample entry follows stsd's version, flags and entry count; a visual one holds the frame size.
    val entry = child(bytes, stbl, "stsd").start + 8
    val codec = String(bytes, entry + 4, 4, Charsets.US_ASCII)
    val stts = child(bytes, stbl, "stts")
    var frames = 0L
    var ticks = 0L
    repeat(u32(bytes, stts.start + 4).toInt()) { i ->
        val count = u32(bytes, stts.start + 8 + i * 8)
        frames += count
        ticks += count * u32(bytes, stts.start + 12 + i * 8)
    }
    return Track(handler, codec, u16(bytes, entry + 32), u16(bytes, entry + 34), frames.toInt(), ticks.toDouble() / timescale)
}

private fun u16(bytes: ByteArray, at: Int): Int = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)

private fun u32(bytes: ByteArray, at: Int): Long = (0 until 4).fold(0L) { acc, i -> (acc shl 8) or (bytes[at + i].toLong() and 0xFF) }

private fun u64(bytes: ByteArray, at: Int): Long = (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (bytes[at + i].toLong() and 0xFF) }
