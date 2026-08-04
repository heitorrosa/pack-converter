package dev.heitorrosa.packconverter.pack;

import dev.heitorrosa.packconverter.convert.ArmorExpansion;
import dev.heitorrosa.packconverter.convert.ModelRewriter;
import dev.heitorrosa.packconverter.convert.PathRemapper;
import dev.heitorrosa.packconverter.convert.RenameMap;
import dev.heitorrosa.packconverter.convert.SliceEngine;
import dev.heitorrosa.packconverter.convert.SliceSpecLoader;
import dev.heitorrosa.packconverter.convert.Slicer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * {@link PackResources} wrapper that presents a legacy (1.8-era) pack under
 * modern names: resource paths are remapped through {@link PathRemapper},
 * model/blockstate JSON texture references are rewritten through
 * {@link ModelRewriter}, and the pack metadata advertises the current pack
 * format so the game accepts the pack as compatible.
 *
 * <p>Composition over a delegate; the delegate's streams are always closed.
 */
public final class ConverterPackResources implements PackResources {

    private static final RenameMap RENAME_MAP = RenameMap.fromDefaultResources();
    private static final PathRemapper REMAPPER = new PathRemapper(RENAME_MAP);
    private static final ModelRewriter REWRITER = new ModelRewriter();
    /**
     * Slices the legacy GUI/particles/item atlases (and the custom explosion
     * splice) into the modern per-sprite files the game requests.
     */
    private static final SliceEngine SLICE_ENGINE = new SliceEngine(
            SliceSpecLoader.fromDefaultResources(), new Slicer());
    /**
     * One-to-many pixel copies for armor layers and the menu background /
     * mojang logo / sun renames (26.2 serves these directly, not from an
     * atlas).
     */
    private static final ArmorExpansion ARMOR = new ArmorExpansion();
    /**
     * Modern base name -> legacy base names that rename to it (see
     * {@link RenameMap#reverseIndex()}); used to probe the delegate for a
     * resource when the direct modern-path lookup misses, because
     * {@link PathRemapper} only maps legacy -> modern.
     */
    private static final Map<String, List<String>> REVERSE = RENAME_MAP.reverseIndex();

    private final PackResources delegate;
    private boolean closed;

    public ConverterPackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
        // Slice/expansion first: the GUI atlas sprites, particles, armor and
        // copy renames do not exist as files in the delegate, so they must be
        // synthesized before the rename remap runs for block/item textures.
        byte[] sliced = SLICE_ENGINE.trySlice(id.getPath(), p -> readBytes(type, id, p));
        if (sliced != null) {
            return () -> new ByteArrayInputStream(sliced);
        }
        String expansionSource = ARMOR.sourceForTarget(id.getPath());
        if (expansionSource != null) {
            IoSupplier<InputStream> copied = expansionSource(type, id, expansionSource);
            if (copied != null) {
                return copied; // straight pixel copy, no rewrite
            }
        }
        String path = id.getPath();
        String remappedPath = REMAPPER.remapPath(path);
        Identifier remapped = remappedPath.equals(path) ? id : id.withPath(remappedPath);

        IoSupplier<InputStream> supplier = delegate.getResource(type, remapped);
        if (supplier == null && !remapped.equals(id)) {
            supplier = delegate.getResource(type, id);
        }
        if (supplier == null) {
            supplier = probeReverse(type, id, remapped);
        }
        if (supplier == null) {
            return null;
        }
        if (isModelJson(path)) {
            return rewriteJsonSupplier(supplier, path);
        }
        return supplier;
    }

    @Override
    public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
        Set<Identifier> emitted = new HashSet<>();
        listInto(type, namespace, directory, output, emitted);
        for (String probe : legacyProbes(directory)) {
            listInto(type, namespace, probe, output, emitted);
        }
        emitSliceTargets(type, namespace, directory, output, emitted);
        emitExpansionTargets(type, namespace, directory, output, emitted);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        // Raw passthrough: the modern pack.mcmeta is served via getMetadataSection.
        return delegate.getRootResource(path);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
        if (type == PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES)) {
            return (T) modernPackMetadataSection();
        }
        return delegate.getMetadataSection(type);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public void close() {
        // Idempotent: the game may close a resource pack more than once.
        if (!closed) {
            closed = true;
            delegate.close();
        }
    }

    /**
     * Rewrites a model/blockstate JSON on read: the delegate's stream is
     * drained and closed inside a try-with-resources, and an in-memory stream
     * over the rewritten bytes is returned. Shared by {@link #getResource} and
     * {@link #listInto} so listed models/blockstates are rewritten exactly
     * like directly fetched ones (26.2 loads every model from the listing).
     */
    private IoSupplier<InputStream> rewriteJsonSupplier(IoSupplier<InputStream> supplier, String path) {
        return () -> {
            try (InputStream in = supplier.get()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                String rewritten = path.contains("models/")
                        ? REWRITER.rewriteModelJson(json, REMAPPER)
                        : REWRITER.rewriteBlockStateJson(json, REMAPPER);
                return new ByteArrayInputStream(rewritten.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private void listInto(PackType type, String namespace, String directory, ResourceOutput output,
            Set<Identifier> emitted) {
        delegate.listResources(type, namespace, directory, (id, supplier) -> {
            String remappedPath = REMAPPER.remapPath(id.getPath());
            Identifier out = remappedPath.equals(id.getPath()) ? id : id.withPath(remappedPath);
            // Dedupe: a modern entry (listed directly) wins over the same id
            // surfaced remapped from a probed legacy directory.
            if (emitted.add(out)) {
                IoSupplier<InputStream> served = isModelJson(remappedPath)
                        ? rewriteJsonSupplier(supplier, remappedPath)
                        : supplier;
                output.accept(out, served);
            }
        });
    }

    /**
     * Surfaces slice targets under {@code directory}: the GUI atlas scan of
     * {@code textures/gui/sprites/hud} finds the hotbar sprite even when the
     * old pack only ships {@code textures/gui/widgets.png}. Sliced eagerly at
     * listing time; empty slices are skipped (they would render the vanilla
     * default anyway).
     */
    private void emitSliceTargets(PackType type, String namespace, String directory,
            ResourceOutput output, Set<Identifier> emitted) {
        for (String target : SLICE_ENGINE.sliceTargetsUnder(directory,
                src -> delegate.getResource(type, id(namespace, src)) != null)) {
            Identifier modern = id(namespace, target);
            if (!emitted.add(modern)) {
                continue;
            }
            byte[] sliced = SLICE_ENGINE.trySlice(target, p -> readBytes(type, modern, p));
            if (sliced != null) {
                output.accept(modern, () -> new ByteArrayInputStream(sliced));
            }
        }
    }

    /**
     * Surfaces armor expansion and copy targets under {@code directory} whose
     * source actually exists in the delegate (e.g. the material targets under
     * {@code textures/entity/equipment/humanoid} when the old pack has the
     * layer file). The source stream is drained once, inside a
     * try-with-resources, so the delegate's IoSupplier is never closed twice.
     */
    private void emitExpansionTargets(PackType type, String namespace, String directory,
            ResourceOutput output, Set<Identifier> emitted) {
        for (String target : ARMOR.targetsUnder(directory)) {
            Identifier modern = id(namespace, target);
            if (!emitted.add(modern)) {
                continue;
            }
            IoSupplier<InputStream> source = expansionSource(type, modern, ARMOR.sourceForTarget(target));
            if (source == null) {
                continue;
            }
            try (InputStream in = source.get()) {
                byte[] bytes = in.readAllBytes();
                output.accept(modern, () -> new ByteArrayInputStream(bytes));
            } catch (IOException e) {
                // Unreadable source: skip; the vanilla default will be used.
            }
        }
    }

    /**
     * Serves an expansion source from the delegate, probing the legacy
     * {@code textures/armor/} spelling when the canonical
     * {@code textures/models/armor/} one is absent.
     */
    private IoSupplier<InputStream> expansionSource(PackType type, Identifier id, String sourcePath) {
        IoSupplier<InputStream> source = delegate.getResource(type, id.withPath(sourcePath));
        if (source == null && sourcePath.startsWith("textures/models/armor/")) {
            source = delegate.getResource(type, id.withPath(
                    "textures/armor/" + sourcePath.substring("textures/models/armor/".length())));
        }
        return source;
    }

    /**
     * Drains a delegate resource into memory, closing its stream exactly once.
     * Returns {@code null} when the resource is absent or unreadable.
     */
    private byte[] readBytes(PackType type, Identifier id, String path) {
        IoSupplier<InputStream> supplier = delegate.getResource(type, id.withPath(path));
        if (supplier == null) {
            return null;
        }
        try (InputStream in = supplier.get()) {
            return in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    /**
     * One-way PathRemapper miss: a modern path whose file exists in the
     * delegate only under its legacy name (e.g. a {@code .png.mcmeta}
     * companion, or a modern model id). For every legacy base name that
     * renames to this path's base name, try the legacy folder form against
     * the delegate and return the first hit.
     */
    private IoSupplier<InputStream> probeReverse(PackType type, Identifier id, Identifier remapped) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String folder = slash >= 0 ? path.substring(0, slash + 1) : "";
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = file.indexOf('.');
        String base = dot >= 0 ? file.substring(0, dot) : file;
        String suffix = dot >= 0 ? file.substring(dot) : "";

        List<String> legacyBases = REVERSE.get(base);
        if (legacyBases == null) {
            return null;
        }
        String legacyFolder = legacyFolder(folder);
        for (String legacyBase : legacyBases) {
            String candidate = legacyFolder + legacyBase + suffix;
            if (candidate.equals(path) || candidate.equals(remapped.getPath())) {
                continue; // already tried against the delegate
            }
            IoSupplier<InputStream> supplier = delegate.getResource(type, id.withPath(candidate));
            if (supplier != null) {
                return supplier;
            }
        }
        return null;
    }

    /**
     * Modern folder -> the legacy 1.8 folder spelling; folders with no legacy
     * counterpart (e.g. {@code models/block/}) pass through unchanged, so only
     * the base name is replaced there.
     */
    private static String legacyFolder(String folder) {
        switch (folder) {
            case "textures/block/":
                return "textures/blocks/";
            case "textures/item/":
                return "textures/items/";
            case "textures/models/armor/":
                return "textures/armor/";
            case "block/":
                return "blocks/";
            case "item/":
                return "items/";
            default:
                return folder;
        }
    }

    private PackMetadataSection modernPackMetadataSection() throws IOException {
        PackMetadataSection original = delegate.getMetadataSection(
                PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES));
        Component description = original != null ? original.description() : Component.literal("Converted pack");
        PackFormat current = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        return new PackMetadataSection(description, current.minorRange());
    }

    private static boolean isModelJson(String path) {
        return path.endsWith(".json") && (path.contains("models/") || path.contains("blockstates/"));
    }

    /**
     * Legacy directories to additionally list when a modern directory is
     * requested (and vice versa): {@code textures/block} and {@code block}
     * probe the plural legacy forms, and the reverse. The {@code textures}
     * root needs no probe — a root listing already surfaces every subpath,
     * remapped.
     */
    private static String[] legacyProbes(String directory) {
        switch (directory) {
            case "textures/block":
                return new String[] {"textures/blocks"};
            case "textures/item":
                return new String[] {"textures/items"};
            case "textures/blocks":
                return new String[] {"textures/block"};
            case "textures/items":
                return new String[] {"textures/item"};
            case "block":
                return new String[] {"blocks"};
            case "item":
                return new String[] {"items"};
            case "blocks":
                return new String[] {"block"};
            case "items":
                return new String[] {"item"};
            default:
                return new String[0];
        }
    }
}
