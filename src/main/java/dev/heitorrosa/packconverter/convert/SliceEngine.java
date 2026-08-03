package dev.heitorrosa.packconverter.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Runtime coordinator for the GUI/armor/particles slice engine: answers a
 * modern resource request by slicing the legacy source atlas on demand, and
 * lists the slice targets that can be served for a directory.
 *
 * <p>The 1.8 explosion atlas (4x4 grid of 32x32 cells, row-major) is a custom
 * splice not present in particles.json; its 16 targets are added to the index
 * at construction. Spec boxes are scaled from the declared vanilla source
 * size to the pack's actual atlas size before slicing. Immutable after
 * construction (thread-safe).
 */
public final class SliceEngine {

    private static final String EXPLOSION_SOURCE = "textures/entity/explosion.png";

    private final Map<String, SliceEntry> reverse;
    private final Slicer slicer;

    public SliceEngine(SliceSpecLoader loader, Slicer slicer) {
        Map<String, SliceEntry> reverse = new HashMap<>(loader.reverseIndex());
        for (int i = 0; i < 16; i++) {
            String target = "textures/particle/explosion_" + i + ".png";
            // The 1.8 explosion atlas is a 128x128 4x4 grid of 32x32 cells.
            reverse.put(target, new SliceEntry(EXPLOSION_SOURCE,
                    new SliceTarget(target, (i % 4) * 32, (i / 4) * 32, 32, 32), 128, 128));
        }
        this.reverse = Collections.unmodifiableMap(reverse);
        this.slicer = slicer;
    }

    /**
     * @param targetPath requested modern pack-relative path
     * @param sourceReader reads the source atlas bytes by pack-relative path;
     *                     may be {@code null} for a missing source
     * @return the sliced PNG bytes, or {@code null} when the target is not a
     *         slice target, the source is missing, or the slice is empty/out
     *         of bounds (caller falls through to the default)
     */
    public byte[] trySlice(String targetPath, Function<String, byte[]> sourceReader) {
        SliceEntry entry = reverse.get(targetPath);
        if (entry == null || sourceReader == null) {
            return null;
        }
        byte[] source = sourceReader.apply(entry.sourcePath());
        if (source == null) {
            return null;
        }
        SliceTarget target = entry.target();
        int[] dims = slicer.dimensions(source);
        if (dims == null) {
            return null;
        }
        // Spec boxes are calibrated against the declared vanilla source size;
        // scale them by the ACTUAL source size so 2x HD (or downscaled) packs
        // slice at the scaled cells. A missing/zero declared size means 1.0.
        double scale = Math.min(
                entry.declaredWidth() > 0 ? (double) dims[0] / entry.declaredWidth() : 1.0,
                entry.declaredHeight() > 0 ? (double) dims[1] / entry.declaredHeight() : 1.0);
        int x = (int) Math.round(target.x() * scale);
        int y = (int) Math.round(target.y() * scale);
        int w = Math.max(1, (int) Math.round(target.width() * scale));
        int h = Math.max(1, (int) Math.round(target.height() * scale));
        byte[] sliced = slicer.slice(source, x, y, w, h);
        if (sliced == null || slicer.isEmpty(sliced)) {
            return null;
        }
        return sliced;
    }

    /**
     * @return all slice targets whose path is under {@code directoryPath} and
     *         whose source atlas exists according to {@code sourceExists}
     */
    public List<String> sliceTargetsUnder(String directoryPath, Predicate<String> sourceExists) {
        String prefix = directoryPath.isEmpty() ? "" : directoryPath + "/";
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, SliceEntry> entry : reverse.entrySet()) {
            if (entry.getKey().startsWith(prefix) && sourceExists.test(entry.getValue().sourcePath())) {
                out.add(entry.getKey());
            }
        }
        return out;
    }
}
