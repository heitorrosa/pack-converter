package dev.heitorrosa.packconverter.convert;

import java.util.List;

/**
 * A parsed mapping entry: a legacy source atlas (pack-relative path, e.g.
 * {@code textures/gui/widgets.png}), the vanilla source dimensions it was
 * calibrated against (0 when the spec does not declare them), and the modern
 * targets sliced out of it. The declared dimensions let the slice engine
 * scale the boxes to the pack's actual atlas size (e.g. a 2x HD pack).
 */
public record SliceSpec(String sourcePath, int declaredWidth, int declaredHeight,
        List<SliceTarget> targets) {
}
