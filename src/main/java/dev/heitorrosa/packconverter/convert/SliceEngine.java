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
 * at construction. Immutable after construction (thread-safe).
 */
public final class SliceEngine {

    private static final String EXPLOSION_SOURCE = "textures/entity/explosion.png";

    private final Map<String, SliceEntry> reverse;
    private final Slicer slicer;

    public SliceEngine(SliceSpecLoader loader, Slicer slicer) {
        Map<String, SliceEntry> reverse = new HashMap<>(loader.reverseIndex());
        for (int i = 0; i < 16; i++) {
            String target = "textures/particle/explosion_" + i + ".png";
            reverse.put(target, new SliceEntry(EXPLOSION_SOURCE,
                    new SliceTarget(target, (i % 4) * 32, (i / 4) * 32, 32, 32)));
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
        byte[] sliced = slicer.slice(source, target.x(), target.y(), target.width(), target.height());
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
