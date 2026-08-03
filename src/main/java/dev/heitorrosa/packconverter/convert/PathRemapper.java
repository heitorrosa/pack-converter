package dev.heitorrosa.packconverter.convert;

import java.util.Map;

/**
 * Remaps a resource path (Identifier path form, e.g. {@code
 * textures/blocks/planks_oak.png}) from the 1.8 layout to the modern layout:
 * folder normalization, base-name renaming through {@link RenameMap}, with the
 * extension and any namespace prefix preserved.
 */
public final class PathRemapper {

    /**
     * Extra base-name renames scoped to a legacy folder, applied after the
     * JSON rename chain: the mojang logo and the sun texture moved to a
     * subfolder in modern versions. Folder-scoped so a modern path that
     * already lives in the target location (e.g.
     * {@code textures/environment/celestial/sun.png}) is never remapped again.
     */
    private static final Map<String, Map<String, String>> FOLDER_SCOPED_RENAMES = Map.of(
            "textures/gui/title/", Map.of("mojang", "mojangstudios"),
            "textures/environment/", Map.of("sun", "celestial/sun"));

    private final RenameMap map;

    /**
     * @param map rename table; may be {@code null} when only
     *            {@link #isLegacyPath(String)} will be used
     */
    public PathRemapper(RenameMap map) {
        this.map = map;
    }

    /**
     * Remaps a single resource path. Folder normalization is unconditional for
     * rename folders ({@code textures/blocks/}, {@code textures/items/}, ...);
     * the base name is always run through the rename map. Paths in
     * non-rename folders (entity, gui, ...) pass through unchanged unless the
     * base name actually matches a rename.
     */
    public String remapPath(String path) {
        int colon = path.indexOf(':');
        String namespace = colon >= 0 ? path.substring(0, colon + 1) : "";
        String rest = colon >= 0 ? path.substring(colon + 1) : path;

        int slash = rest.lastIndexOf('/');
        String folder = slash >= 0 ? rest.substring(0, slash + 1) : "";
        String file = slash >= 0 ? rest.substring(slash + 1) : rest;

        // Base name = everything before the first '.' (handles .png.mcmeta
        // companions: "planks_oak.png.mcmeta" -> base "planks_oak").
        int dot = file.indexOf('.');
        String base = dot >= 0 ? file.substring(0, dot) : file;
        String suffix = dot >= 0 ? file.substring(dot) : "";

        String renamed = map == null ? base : map.rename(base);
        Map<String, String> folderRenames = FOLDER_SCOPED_RENAMES.get(folder);
        if (folderRenames != null) {
            String extra = folderRenames.get(renamed);
            if (extra != null) {
                renamed = extra;
            }
        }
        if (!isRenameFolder(folder) && renamed.equals(base)) {
            // Non-rename folder with no match: pass through completely untouched.
            return namespace + rest;
        }
        return namespace + normalizeFolder(folder) + renamed + suffix;
    }

    /**
     * True iff the path lives in one of the legacy 1.8 texture folders:
     * {@code textures/blocks/}, {@code textures/items/}, {@code blocks/} or
     * {@code items/}.
     */
    public boolean isLegacyPath(String path) {
        return path.contains("textures/blocks/")
                || path.contains("textures/items/")
                || path.contains("blocks/")
                || path.contains("items/");
    }

    private static boolean isRenameFolder(String folder) {
        switch (folder) {
            case "textures/blocks/":
            case "textures/items/":
            case "blocks/":
            case "items/":
            case "block/":
            case "item/":
                return true;
            default:
                return false;
        }
    }

    private static String normalizeFolder(String folder) {
        switch (folder) {
            case "textures/blocks/":
                return "textures/block/";
            case "textures/items/":
                return "textures/item/";
            case "blocks/":
                return "block/";
            case "items/":
                return "item/";
            default:
                return folder;
        }
    }
}
