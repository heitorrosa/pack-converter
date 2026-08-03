package dev.heitorrosa.packconverter.convert;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Test helper: builds tiny PNGs programmatically with ImageIO. Pixel
 * {@code (x, y)} is drawn with the deterministic RGB value {@code x + y*16}
 * (opaque), so slice assertions can compare exact pixels against the source.
 */
public final class PngTestUtil {

    private PngTestUtil() {
    }

    /** Opaque WxH PNG, pixel (x,y) colored {@code x + y*16}. */
    public static byte[] png(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, 0xFF000000 | ((x + y * 16) & 0xFFFFFF));
            }
        }
        return encode(img);
    }

    /** Fully transparent WxH PNG (every alpha == 0). */
    public static byte[] transparentPng(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
    }

    /** 4x4 PNG with a single opaque pixel at (1,1), rest transparent. */
    public static byte[] pngWithOneOpaquePixel() {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(1, 1, 0xFFFFFFFF);
        return encode(img);
    }

    /** Opaque RGB (no alpha channel) WxH PNG; pixel (x,y) colored {@code x + y*16}. */
    public static byte[] rgbPng(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, 0xFF000000 | ((x + y * 16) & 0xFFFFFF));
            }
        }
        return encode(img);
    }

    public static int width(byte[] pngBytes) {
        return read(pngBytes).getWidth();
    }

    public static int height(byte[] pngBytes) {
        return read(pngBytes).getHeight();
    }

    /** RGB value (0..0xFFFFFF) of the pixel at (x, y). */
    public static int rgb(byte[] pngBytes, int x, int y) {
        return read(pngBytes).getRGB(x, y) & 0xFFFFFF;
    }

    private static BufferedImage read(byte[] pngBytes) {
        try (InputStream in = new ByteArrayInputStream(pngBytes)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] encode(BufferedImage img) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
