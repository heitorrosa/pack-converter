package dev.heitorrosa.packconverter.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConverterPackResources}: path remapping, model JSON
 * rewriting, metadata rewriting, listing with legacy-directory probing, and
 * lifecycle delegation. The delegate is an in-memory {@link FakePackResources}
 * keyed by Identifier path.
 */
class ConverterPackResourcesTest {

    private static final PackType CLIENT = PackType.CLIENT_RESOURCES;

    @BeforeAll
    static void setGameVersion() {
        // SharedConstants.getCurrentVersion() throws before the game bootstrap
        // calls setVersion(); mirror it with the built-in version so the
        // current pack format is resolvable in the bare test JVM.
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
    }

    private FakePackResources fake;
    private ConverterPackResources converter;

    @BeforeEach
    void setUp() {
        fake = new FakePackResources();
        converter = new ConverterPackResources(fake);
    }

    @Test
    void getResourceRemapsLegacyTextureToModernPath() throws IOException {
        fake.put("textures/block/oak_planks.png", "modern".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/blocks/planks_oak.png"));

        assertNotNull(supplier);
        assertEquals("modern", read(supplier));
    }

    @Test
    void remappedVariantWinsOverOriginal() throws IOException {
        fake.put("textures/blocks/planks_oak.png", "legacy".getBytes(StandardCharsets.UTF_8));
        fake.put("textures/block/oak_planks.png", "modern".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/blocks/planks_oak.png"));

        assertEquals("modern", read(supplier));
    }

    @Test
    void unmappedNameFallsBackToOriginal() throws IOException {
        // "custom_thing" is not in the rename map; the folder-normalized modern
        // path is absent, so the original legacy path must be returned.
        fake.put("textures/blocks/custom_thing.png", "original".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/blocks/custom_thing.png"));

        assertNotNull(supplier);
        assertEquals("original", read(supplier));
    }

    @Test
    void missingResourceReturnsNull() {
        assertNull(converter.getResource(CLIENT, id("textures/block/does_not_exist.png")));
    }

    @Test
    void modelJsonIsRewritten() throws IOException {
        fake.put("models/block/old_model.json",
                "{\"textures\":{\"all\":\"block/planks_oak\"}}".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("models/block/old_model.json"));

        assertNotNull(supplier);
        assertTrue(read(supplier).contains("block/oak_planks"));
    }

    @Test
    void blockStateJsonModelKeysAreRewritten() throws IOException {
        String blockState = "{\"variants\":{\"normal\":{\"model\":\"block/planks_oak\"}}}";
        fake.put("blockstates/planks_oak.json", blockState.getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("blockstates/planks_oak.json"));

        assertNotNull(supplier);
        String out = read(supplier);
        assertTrue(out.contains("block/oak_planks"));
        assertFalse(out.contains("block/planks_oak"));
    }

    @Test
    void getMetadataSectionReportsCurrentFormatRange() throws IOException {
        // The delegate's own range (0..1) excludes the current version; the
        // assertion below only passes when the converter overrides it.
        PackFormat current = SharedConstants.getCurrentVersion().packVersion(CLIENT);
        assertFalse(fake.getMetadataSection(PackMetadataSection.forPackType(CLIENT))
                .supportedFormats().isValueInRange(current));

        PackMetadataSection section = converter
                .getMetadataSection(PackMetadataSection.forPackType(CLIENT));

        assertNotNull(section);
        assertEquals("Fake pack", section.description().getString());
        assertTrue(section.supportedFormats().isValueInRange(current));
    }

    @Test
    void nonPackMetadataSectionsDelegate() throws IOException {
        MetadataSectionType<String> other = new MetadataSectionType<>("other", Codec.STRING);
        assertNull(converter.getMetadataSection(other));
    }

    @Test
    void listResourcesRemapsEntries() throws IOException {
        fake.put("textures/blocks/planks_oak.png", "x".getBytes(StandardCharsets.UTF_8));

        List<Identifier> collected = collect("textures/blocks");

        assertTrue(collected.contains(id("textures/block/oak_planks.png")));
        assertFalse(collected.contains(id("textures/blocks/planks_oak.png")));
    }

    @Test
    void listResourcesProbesLegacyDirectories() throws IOException {
        // Delegate only holds legacy "textures/blocks" entries; requesting the
        // modern "textures/block" directory must surface them, remapped.
        fake.put("textures/blocks/planks_oak.png", "x".getBytes(StandardCharsets.UTF_8));

        List<Identifier> collected = collect("textures/block");

        assertTrue(collected.contains(id("textures/block/oak_planks.png")));
    }

    @Test
    void listResourcesRewritesModelJsonContent() throws IOException {
        fake.put("models/block/planks_oak.json",
                "{\"textures\":{\"all\":\"block/planks_oak\"}}".getBytes(StandardCharsets.UTF_8));

        Map<Identifier, String> collected = collectWithContent("models/block");

        Identifier remapped = id("models/block/oak_planks.json");
        assertTrue(collected.containsKey(remapped));
        assertTrue(collected.get(remapped).contains("block/oak_planks"));
        assertFalse(collected.get(remapped).contains("block/planks_oak"));
    }

    @Test
    void listResourcesRewritesBlockStateJsonContent() throws IOException {
        fake.put("blockstates/planks_oak.json",
                "{\"variants\":{\"normal\":{\"model\":\"block/planks_oak\"}}}".getBytes(StandardCharsets.UTF_8));

        Map<Identifier, String> collected = collectWithContent("blockstates");

        Identifier remapped = id("blockstates/oak_planks.json");
        assertTrue(collected.containsKey(remapped));
        assertTrue(collected.get(remapped).contains("block/oak_planks"));
        assertFalse(collected.get(remapped).contains("block/planks_oak"));
    }

    @Test
    void getResourceFindsMcmetaCompanionViaReverseProbe() throws IOException {
        // 26.2 asks for the modern companion path; the legacy pack only has the
        // old-named file. Reverse lookup must surface it.
        fake.put("textures/blocks/planks_oak.png.mcmeta", "anim".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("textures/block/oak_planks.png.mcmeta"));

        assertNotNull(supplier);
        assertEquals("anim", read(supplier));
    }

    @Test
    void getResourceFindsModernModelViaLegacyName() throws IOException {
        // Delegate has the model under its legacy file name; the game requests
        // the modern id. The reverse probe finds it and the content is rewritten.
        fake.put("models/block/planks_oak.json",
                "{\"textures\":{\"all\":\"block/planks_oak\"}}".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getResource(CLIENT, id("models/block/oak_planks.json"));

        assertNotNull(supplier);
        assertTrue(read(supplier).contains("block/oak_planks"));
    }

    @Test
    void supplierWrapsOpenedResourcesAndCloseIsIdempotent() {
        FakeResourcesSupplier delegate = new FakeResourcesSupplier();
        ConverterResourcesSupplier supplier = new ConverterResourcesSupplier(delegate);

        PackResources primary = supplier.openPrimary(FakePackResources.FAKE_LOCATION);
        PackResources full = supplier.openFull(FakePackResources.FAKE_LOCATION,
                new Pack.Metadata(Component.literal("x"), PackCompatibility.COMPATIBLE,
                        FeatureFlagSet.of(), List.of()));

        assertTrue(primary instanceof ConverterPackResources);
        assertTrue(full instanceof ConverterPackResources);

        primary.close();
        primary.close();
        assertEquals(1, delegate.resources.closeCount);
        full.close();
        full.close();
        assertEquals(2, delegate.resources.closeCount);
    }

    @Test
    void getRootResourcePassesThrough() throws IOException {
        fake.put("pack.mcmeta", "{\"pack\":{}}".getBytes(StandardCharsets.UTF_8));

        IoSupplier<InputStream> supplier = converter.getRootResource("pack.mcmeta");

        assertNotNull(supplier);
        assertEquals("{\"pack\":{}}", read(supplier));
    }

    @Test
    void getNamespacesDelegates() {
        fake.namespaces.add("minecraft");
        assertTrue(converter.getNamespaces(CLIENT).contains("minecraft"));
    }

    @Test
    void closeDelegatesAndIsIdempotent() {
        converter.close();
        converter.close();
        assertEquals(1, fake.closeCount);
    }

    private List<Identifier> collect(String directory) {
        List<Identifier> collected = new ArrayList<>();
        converter.listResources(CLIENT, "minecraft", directory, (id, supplier) -> collected.add(id));
        return collected;
    }

    private Map<Identifier, String> collectWithContent(String directory) throws IOException {
        Map<Identifier, String> collected = new HashMap<>();
        converter.listResources(CLIENT, "minecraft", directory,
                (id, supplier) -> {
                    try {
                        collected.put(id, read(supplier));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        return collected;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }

    private static String read(IoSupplier<InputStream> supplier) throws IOException {
        try (InputStream in = supplier.get()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * In-memory {@link PackResources} keyed by Identifier path (no namespace
     * prefix). {@link #location()} returns a minimal fake {@code
     * PackLocationInfo}; {@code getMetadataSection} serves a fixed pack
     * section so callers can read a description.
     */
    private static final class FakePackResources implements PackResources {

        static final PackLocationInfo FAKE_LOCATION = new PackLocationInfo(
                "fake", Component.literal("Fake pack"), PackSource.BUILT_IN, Optional.empty());
        private final Map<String, byte[]> resources = new HashMap<>();
        private final Set<String> namespaces = new HashSet<>();
        private int closeCount;

        void put(String path, byte[] bytes) {
            resources.put(path, bytes);
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            String key = String.join("/", path);
            byte[] bytes = resources.get(key);
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            byte[] bytes = resources.get(id.getPath());
            return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
        }

        @Override
        public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
            String prefix = directory + "/";
            for (Map.Entry<String, byte[]> entry : resources.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    Identifier id = Identifier.fromNamespaceAndPath(namespace, entry.getKey());
                    byte[] bytes = entry.getValue();
                    output.accept(id, () -> new ByteArrayInputStream(bytes));
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return namespaces;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMetadataSection(MetadataSectionType<T> type) {
            if (type == PackMetadataSection.forPackType(PackType.CLIENT_RESOURCES)) {
                return (T) new PackMetadataSection(Component.literal("Fake pack"),
                        new InclusiveRange<>(PackFormat.of(0), PackFormat.of(1)));
            }
            return null;
        }

        @Override
        public PackLocationInfo location() {
            return FAKE_LOCATION;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    /**
     * {@link Pack.ResourcesSupplier} whose opened resources share one fake
     * pack, recording how many times each open method was invoked.
     */
    private static final class FakeResourcesSupplier implements Pack.ResourcesSupplier {

        private final FakePackResources resources = new FakePackResources();

        @Override
        public PackResources openPrimary(PackLocationInfo location) {
            return resources;
        }

        @Override
        public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
            return resources;
        }
    }
}
