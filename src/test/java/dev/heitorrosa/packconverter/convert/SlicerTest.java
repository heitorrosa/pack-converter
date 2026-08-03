package dev.heitorrosa.packconverter.convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Slicer}: pixel-exact slicing, out-of-bounds rejection and
 * transparency detection.
 */
class SlicerTest {

    private final Slicer slicer = new Slicer();

    @Test
    void slicesBoxFromSource() {
        byte[] source = PngTestUtil.png(128, 128);

        byte[] sliced = slicer.slice(source, 16, 16, 16, 16);

        assertNotNull(sliced);
        assertEquals(16, PngTestUtil.width(sliced));
        assertEquals(16, PngTestUtil.height(sliced));
        // Slice pixel (5, 7) == source pixel (21, 23) with color x + y*16.
        assertEquals(PngTestUtil.rgb(source, 21, 23), PngTestUtil.rgb(sliced, 5, 7));
        assertEquals(PngTestUtil.rgb(source, 16, 16), PngTestUtil.rgb(sliced, 0, 0));
    }

    @Test
    void fullSourceSliceIsIdentical() {
        byte[] source = PngTestUtil.png(64, 64);

        byte[] sliced = slicer.slice(source, 0, 0, 64, 64);

        assertNotNull(sliced);
        assertEquals(PngTestUtil.rgb(source, 63, 63), PngTestUtil.rgb(sliced, 63, 63));
    }

    @Test
    void outOfBoundsBoxReturnsNull() {
        byte[] source = PngTestUtil.png(128, 128);
        assertNull(slicer.slice(source, 120, 120, 16, 16)); // x+w > width
        assertNull(slicer.slice(source, 0, 120, 8, 16)); // y+h > height
        assertNull(slicer.slice(source, -1, 0, 8, 8)); // negative x
        assertNull(slicer.slice(source, 0, 0, 0, 8)); // zero width
    }

    @Test
    void transparentImageIsEmpty() {
        assertTrue(slicer.isEmpty(PngTestUtil.transparentPng(8, 8)));
    }

    @Test
    void opaqueImageIsNotEmpty() {
        assertFalse(slicer.isEmpty(PngTestUtil.png(8, 8)));
    }

    @Test
    void partiallyOpaqueImageIsNotEmpty() {
        // 1x1 opaque among transparent pixels still counts as non-empty.
        byte[] bytes = PngTestUtil.pngWithOneOpaquePixel();
        assertFalse(slicer.isEmpty(bytes));
    }

    @Test
    void noAlphaRgbImageIsNotEmpty() {
        // A no-alpha RGB image must never count as empty.
        assertFalse(slicer.isEmpty(PngTestUtil.rgbPng(4, 4)));
    }

    @Test
    void corruptBytesAreTreatedAsEmpty() {
        assertTrue(slicer.isEmpty(new byte[] {1, 2, 3, 4}));
    }
}
