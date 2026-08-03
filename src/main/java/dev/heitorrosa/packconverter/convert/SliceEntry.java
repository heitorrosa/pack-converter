package dev.heitorrosa.packconverter.convert;

/**
 * Reverse-index entry: the source atlas, the target that is sliced from it,
 * and the source's declared vanilla dimensions (used to scale the box to the
 * pack's actual atlas size). Keyed by the target's modern pack-relative path
 * for request-time lookup.
 */
public record SliceEntry(String sourcePath, SliceTarget target,
        int declaredWidth, int declaredHeight) {
}
