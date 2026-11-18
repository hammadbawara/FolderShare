package com.hz_apps.foldershare.ui.components

import com.hz_apps.foldershare.core.util.AppLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrCodeGeneratorTest {

    @Test
    fun encode_downloadUrl_generatesValidQrMatrixWithFinderAndTimingPatterns() {
        val matrix = QrCodeGenerator.encode(AppLinks.DOWNLOAD_URL)

        // DOWNLOAD_URL is 51 bytes -> fits in Version 3 (55 bytes capacity), size = 3 * 4 + 17 = 29
        assertEquals(29, matrix.size)

        // Verify top-left 7x7 finder pattern outer ring and center 3x3
        for (i in 0..6) {
            assertTrue(matrix.get(i, 0), "Top-left finder top border at x=$i")
            assertTrue(matrix.get(i, 6), "Top-left finder bottom border at x=$i")
            assertTrue(matrix.get(0, i), "Top-left finder left border at y=$i")
            assertTrue(matrix.get(6, i), "Top-left finder right border at y=$i")
        }
        assertFalse(matrix.get(1, 1), "Top-left finder inner white ring")
        assertTrue(matrix.get(3, 3), "Top-left finder center dark module")

        // Verify white separator around top-left finder
        for (i in 0..7) {
            assertFalse(matrix.get(i, 7), "Top-left horizontal separator at x=$i")
            assertFalse(matrix.get(7, i), "Top-left vertical separator at y=$i")
        }

        // Verify timing pattern alternation at row 6 / col 6 between finders
        for (i in 8 until matrix.size - 8) {
            assertEquals(i % 2 == 0, matrix.get(i, 6), "Horizontal timing pattern at x=$i")
            assertEquals(i % 2 == 0, matrix.get(6, i), "Vertical timing pattern at y=$i")
        }

        // Verify always-dark module at (8, size - 8)
        assertTrue(matrix.get(8, matrix.size - 8))
    }

    @Test
    fun encode_shortText_usesVersion1Matrix() {
        val matrix = QrCodeGenerator.encode("https://hz.io")
        // 13 bytes -> fits in Version 1 (19 bytes capacity), size = 21
        assertEquals(21, matrix.size)
        assertTrue(matrix.get(3, 3))
        assertTrue(matrix.get(matrix.size - 4, 3))
        assertTrue(matrix.get(3, matrix.size - 4))
    }
}
