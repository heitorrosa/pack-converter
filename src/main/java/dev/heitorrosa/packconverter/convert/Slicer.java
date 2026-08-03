package dev.heitorrosa.packconverter.convert;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Pure image utility over {@link javax.imageio.ImageIO}: cuts a box out of a
 * PNG byte array and detects fully-transparent images. All methods return
 * {@code null}/{@code true} on corrupt input instead of throwing, so the
 * conversion pipeline degrades to the vanilla default instead of crashing.
 * Stateless and thread-safe.
 */
public final class Slicer {

    /**
     * @return PNG bytes of the {@code w}x{@code h} box at ({@code x}, {@code y})
     *         of the source image, or {@code null} if the box is out of bounds
     *         or the input is not a decodable image
     */
    public byte[] slice(byte[] pngBytes, int x, int y, int w, int h) {
        if (pngBytes == null || w <= 0 || h <= 0) {
            return null;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (source == null || x < 0 || y < 0
                    || x + w > source.getWidth() || y + h > source.getHeight()) {
                return null;
            }
            int type = source.getType() == BufferedImage.TYPE_CUSTOM
                    || source.getType() == BufferedImage.TYPE_BYTE_INDEXED
                    ? BufferedImage.TYPE_INT_ARGB : source.getType();
            BufferedImage out = new BufferedImage(w, h, type);
            Graphics2D g = out.createGraphics();
            try {
                g.drawImage(source.getSubimage(x, y, w, h), 0, 0, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "png", baos)) {
                return null;
            }
            return baos.toByteArray();
        } catch (IOException | RuntimeException e) {
            // Trust boundary: a malformed pack image must not take the game down.
            return null;
        }
    }

    /**
     * @return true iff the image is fully transparent (every alpha == 0), has
     *         no pixels, or is not decodable
     */
    public boolean isEmpty(byte[] pngBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (img == null) {
                return true;
            }
            if (!img.getColorModel().hasAlpha()) {
                return false;
            }
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) >>> 24) != 0) {
                        return false;
                    }
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }
}
