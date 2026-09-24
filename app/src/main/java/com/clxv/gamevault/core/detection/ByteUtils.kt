package com.clxv.gamevault.core.detection

internal fun ByteArray.startsWithAt(offset: Int, magic: ByteArray): Boolean {
    if (offset < 0 || offset + magic.size > size) return false
    for (i in magic.indices) if (this[offset + i] != magic[i]) return false
    return true
}

internal fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

internal fun ByteArray.indexOfSub(sub: ByteArray, from: Int = 0): Int {
    outer@ for (i in from..(size - sub.size)) {
        for (j in sub.indices) if (this[i + j] != sub[j]) continue@outer
        return i
    }
    return -1
}

internal fun ByteArray.readAscii(offset: Int, len: Int): String {
    if (offset < 0 || offset + len > size) return ""
    return String(this, offset, len, Charsets.US_ASCII)
}

internal fun ByteArray.readInt32BE(offset: Int): Int {
    if (offset < 0 || offset + 4 > size) return 0
    return ((this[offset].toInt() and 0xFF) shl 24) or
           ((this[offset + 1].toInt() and 0xFF) shl 16) or
           ((this[offset + 2].toInt() and 0xFF) shl 8) or
           (this[offset + 3].toInt() and 0xFF)
}

internal fun ByteArray.readInt32LE(offset: Int): Int {
    if (offset < 0 || offset + 4 > size) return 0
    return (this[offset].toInt() and 0xFF) or
           ((this[offset + 1].toInt() and 0xFF) shl 8) or
           ((this[offset + 2].toInt() and 0xFF) shl 16) or
           ((this[offset + 3].toInt() and 0xFF) shl 24)
}
