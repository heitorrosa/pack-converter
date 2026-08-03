package dev.heitorrosa.packconverter.convert;

/**
 * One slicing target: the modern pack-relative path to produce and the box
 * (in source pixels) to cut out of the source atlas. Box coordinates are
 * top-left anchored; a box whose extent exceeds the source bounds yields no
 * slice (callers fall through to the vanilla default).
 */
public record SliceTarget(String targetPath, int x, int y, int width, int height) {
}
