package dev.heitorrosa.packconverter.convert;

import java.util.List;

/**
 * A parsed mapping entry: a legacy source atlas (pack-relative path, e.g.
 * {@code textures/gui/widgets.png}) and the modern targets sliced out of it.
 */
public record SliceSpec(String sourcePath, List<SliceTarget> targets) {
}
