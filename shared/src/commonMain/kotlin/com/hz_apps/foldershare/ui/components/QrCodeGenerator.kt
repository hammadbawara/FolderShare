package com.hz_apps.foldershare.ui.components

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code Matrix Generator (Byte mode, Error Correction Level L).
 * Supports QR Versions 1..6 (up to 134 bytes), ideal for URLs and local share addresses.
 */
class QrMatrix(
    val size: Int,
    private val modules: Array<BooleanArray>
) {
    fun get(x: Int, y: Int): Boolean = modules[y][x]
}

object QrCodeGenerator {

    // Total data codewords and ECC codewords per single block for Versions 1..6 at EC Level L
    private data class VersionSpec(
        val version: Int,
        val totalDataCodewords: Int,
        val eccCodewordsPerBlock: Int,
        val numBlocks: Int,
        val alignmentCenters: IntArray
    )

    private val VERSION_SPECS = listOf(
        VersionSpec(1, 19, 7, 1, intArrayOf()),
        VersionSpec(2, 34, 10, 1, intArrayOf(6, 18)),
        VersionSpec(3, 55, 15, 1, intArrayOf(6, 22)),
        VersionSpec(4, 80, 20, 1, intArrayOf(6, 26)),
        VersionSpec(5, 108, 26, 1, intArrayOf(6, 30)),
        VersionSpec(6, 136, 18, 2, intArrayOf(6, 34))
    )

    fun encode(text: String): QrMatrix {
        val bytes = text.encodeToByteArray()
        // Mode indicator (4 bits) + Character count (8 bits for v1..9) + 8*N bits
        // Needs to fit in totalDataCodewords * 8 bits
        val spec = VERSION_SPECS.firstOrNull { (bytes.size + 2) <= it.totalDataCodewords }
            ?: VERSION_SPECS.last()

        val dataCodewords = buildDataCodewords(bytes, spec.totalDataCodewords)
        val finalCodewords = interleaveWithEcc(dataCodewords, spec)

        val size = spec.version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(spec, size, modules, isFunction)
        drawCodewords(finalCodewords, size, modules, isFunction)

        // Choose mask with lowest penalty score
        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        var bestModules = modules

        for (mask in 0..7) {
            val masked = Array(size) { y -> modules[y].copyOf() }
            applyMask(mask, size, masked, isFunction)
            drawFormatBits(mask, size, masked, isFunction)
            val penalty = calculatePenalty(size, masked)
            if (penalty < minPenalty) {
                minPenalty = penalty
                bestMask = mask
                bestModules = masked
            }
        }

        drawFormatBits(bestMask, size, bestModules, isFunction)
        return QrMatrix(size, bestModules)
    }

    private fun buildDataCodewords(bytes: ByteArray, capacityBytes: Int): IntArray {
        val maxPayload = minOf(bytes.size, capacityBytes - 2)
        val bits = ArrayList<Int>(capacityBytes * 8)

        fun appendBits(value: Int, length: Int) {
            for (i in length - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator = 0100 (4 bits)
        appendBits(0b0100, 4)
        // Character count indicator (8 bits for version 1..9)
        appendBits(maxPayload, 8)
        for (i in 0 until maxPayload) {
            appendBits(bytes[i].toInt() and 0xFF, 8)
        }

        // Terminator (up to 4 zero bits)
        val capacityBits = capacityBytes * 8
        val terminatorLen = minOf(4, capacityBits - bits.size)
        appendBits(0, terminatorLen)

        // Pad to byte boundary
        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        // Pad bytes 0xEC, 0x11
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < capacityBits) {
            appendBits(padBytes[padIdx % 2], 8)
            padIdx++
        }

        val result = IntArray(capacityBytes)
        for (i in 0 until capacityBytes) {
            var b = 0
            for (bit in 0 until 8) {
                b = (b shl 1) or bits[i * 8 + bit]
            }
            result[i] = b
        }
        return result
    }

    private fun interleaveWithEcc(data: IntArray, spec: VersionSpec): IntArray {
        val numBlocks = spec.numBlocks
        val blockDataLen = spec.totalDataCodewords / numBlocks
        val eccLen = spec.eccCodewordsPerBlock
        val generator = reedSolomonGenerator(eccLen)

        val dataBlocks = Array(numBlocks) { b ->
            data.copyOfRange(b * blockDataLen, (b + 1) * blockDataLen)
        }
        val eccBlocks = Array(numBlocks) { b ->
            reedSolomonRemainder(dataBlocks[b], generator)
        }

        val result = IntArray(spec.totalDataCodewords + numBlocks * eccLen)
        var idx = 0
        for (i in 0 until blockDataLen) {
            for (b in 0 until numBlocks) {
                result[idx++] = dataBlocks[b][i]
            }
        }
        for (i in 0 until eccLen) {
            for (b in 0 until numBlocks) {
                result[idx++] = eccBlocks[b][i]
            }
        }
        return result
    }

    private fun reedSolomonGenerator(degree: Int): IntArray {
        var poly = intArrayOf(1)
        var root = 1
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMultiply(poly[j], root)
            }
            poly = next
            root = gfMultiply(root, 0x02)
        }
        return poly
    }

    private fun reedSolomonRemainder(data: IntArray, generator: IntArray): IntArray {
        val eccLen = generator.size - 1
        val remainder = IntArray(eccLen)
        for (byte in data) {
            val factor = byte xor remainder[0]
            for (i in 0 until eccLen - 1) {
                remainder[i] = remainder[i + 1] xor gfMultiply(generator[i + 1], factor)
            }
            remainder[eccLen - 1] = gfMultiply(generator[eccLen], factor)
        }
        return remainder
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        var a = x
        var b = y
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((b ushr i) and 1) * a)
        }
        return z
    }

    private fun drawFunctionPatterns(
        spec: VersionSpec,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = isDark
                isFunction[y][x] = true
            }
        }

        // Timing patterns
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }

        // Finder patterns + separators (top-left, top-right, bottom-left)
        fun drawFinderPattern(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf( kotlin.math.abs(dx), kotlin.math.abs(dy) )
                    val x = cx + dx
                    val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        val isDark = dist != 2 && dist != 4
                        setFunctionModule(x, y, isDark)
                    }
                }
            }
        }
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (cy in centers) {
            for (cx in centers) {
                val overlapsFinder =
                    (cx == 6 && cy == 6) ||
                    (cx == 6 && cy == size - 7) ||
                    (cx == size - 7 && cy == 6)
                if (!overlapsFinder) {
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                            setFunctionModule(cx + dx, cy + dy, dist != 1)
                        }
                    }
                }
            }
        }

        // Reserve format info areas + Dark module
        for (i in 0..8) {
            if (i != 6) {
                setFunctionModule(8, i, false)
                setFunctionModule(i, 8, false)
            }
        }
        for (i in 0..7) {
            setFunctionModule(size - 1 - i, 8, false)
            setFunctionModule(8, size - 1 - i, false)
        }
        // Always-dark module
        setFunctionModule(8, size - 8, true)
    }

    private fun drawCodewords(
        codewords: IntArray,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        var bitIndex = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var upward = true

        while (right >= 1) {
            if (right == 6) right = 5 // Skip vertical timing column
            for (v in 0 until size) {
                val y = if (upward) (size - 1 - v) else v
                for (j in 0..1) {
                    val x = right - j
                    if (!isFunction[y][x]) {
                        val isDark = if (bitIndex < totalBits) {
                            val byteVal = codewords[bitIndex ushr 3]
                            val bitShift = 7 - (bitIndex and 7)
                            ((byteVal ushr bitShift) and 1) != 0
                        } else {
                            false
                        }
                        modules[y][x] = isDark
                        bitIndex++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x]) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (y / 2 + x / 3) % 2 == 0
                        5 -> (x * y) % 2 + (x * y) % 3 == 0
                        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                        7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                        else -> false
                    }
                    if (invert) {
                        modules[y][x] = !modules[y][x]
                    }
                }
            }
        }
    }

    private fun drawFormatBits(
        mask: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        // EC Level L format indicator = 01
        val data = (0b01 shl 3) or mask
        var rem = data
        for (i in 0 until 10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        fun getBit(idx: Int): Boolean = ((bits ushr idx) and 1) != 0

        // First copy around top-left finder
        for (i in 0..5) modules[i][8] = getBit(i)
        modules[7][8] = getBit(6)
        modules[8][8] = getBit(7)
        modules[8][7] = getBit(8)
        for (i in 9..14) modules[8][14 - i] = getBit(i)

        // Second copy (top-right and bottom-left)
        for (i in 0..7) modules[8][size - 1 - i] = getBit(i)
        for (i in 8..14) modules[size - 15 + i][8] = getBit(i)
        modules[size - 8][8] = true
    }

    private fun calculatePenalty(size: Int, modules: Array<BooleanArray>): Int {
        var penalty = 0
        // Horizontal runs
        for (y in 0 until size) {
            var runColor = modules[y][0]
            var runLen = 1
            for (x in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3
                    else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
        }
        // Vertical runs
        for (x in 0 until size) {
            var runColor = modules[0][x]
            var runLen = 1
            for (y in 1 until size) {
                if (modules[y][x] == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3
                    else if (runLen > 5) penalty += 1
                } else {
                    runColor = modules[y][x]
                    runLen = 1
                }
            }
        }
        // 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y][x]
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    penalty += 3
                }
            }
        }
        return penalty
    }
}
