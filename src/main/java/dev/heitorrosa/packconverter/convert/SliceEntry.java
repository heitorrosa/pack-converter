package dev.heitorrosa.packconverter.convert;

/**
 * Reverse-index entry: the source atlas and the target that is sliced from it.
 * Keyed by the target's modern pack-relative path for request-time lookup.
 */
public record SliceEntry(String sourcePath, SliceTarget target) {
}
