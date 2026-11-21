package dev.heitorrosa.packconverter.mixin;

import dev.heitorrosa.packconverter.convert.OldPackDetector;
import dev.heitorrosa.packconverter.pack.ConverterResourcesSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Converts legacy (1.8-era) client resource packs on the fly. {@code
 * Pack.readMetaAndCreate} is the single seam where folder/zip/builtin packs
 * become {@link Pack} objects, so it is injected at HEAD: the vanilla logic
 * runs first (re-entrantly, guarded by a {@link ThreadLocal}), and only a pack
 * that came back <em>incompatible</em> — and that {@link OldPackDetector}
 * flags as legacy — is replaced by a {@code Pack} whose resources are served
 * through {@link ConverterResourcesSupplier} and whose metadata claims the
 * current pack format. Modern packs pass through untouched.
 */
@Mixin(Pack.class)
public abstract class PackMixin {

    @Unique
    private static final ThreadLocal<Boolean> CONVERTING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "readMetaAndCreate", at = @At("HEAD"), cancellable = true)
    private static void packconverter$convertLegacyPack(PackLocationInfo location, Pack.ResourcesSupplier resources,
            PackType packType, PackSelectionConfig selectionConfig, CallbackInfoReturnable<Pack> cir) {
        if (CONVERTING.get().booleanValue() || packType != PackType.CLIENT_RESOURCES) {
            return;
        }
        CONVERTING.set(Boolean.TRUE);
        try {
            Pack original = Pack.readMetaAndCreate(location, resources, packType, selectionConfig);
            if (original == null || original.getCompatibility().isCompatible()) {
                // Missing metadata or a modern pack: leave the vanilla result alone.
                cir.setReturnValue(original);
                return;
            }
            if (!new OldPackDetector().needsConversion(readRawPackMeta(resources, location))) {
                // Incompatible but not legacy (e.g. TOO_NEW): pass through untouched.
                cir.setReturnValue(original);
                return;
            }
            PackFormat current = SharedConstants.getCurrentVersion().packVersion(packType);
            Pack.Metadata raw = Pack.readPackMetadata(location, resources, current, packType);
            Component description = raw != null ? raw.description() : Component.literal("Converted pack");
            Pack.Metadata modern = new Pack.Metadata(description, PackCompatibility.COMPATIBLE,
                    raw != null ? raw.requestedFeatures() : FeatureFlagSet.of(),
                    raw != null ? raw.overlays() : List.of());
            cir.setReturnValue(new Pack(location, new ConverterResourcesSupplier(resources), modern, selectionConfig));
        } finally {
            CONVERTING.set(Boolean.FALSE);
        }
    }

    @Unique
    private static String readRawPackMeta(Pack.ResourcesSupplier resources, PackLocationInfo location) {
        try (PackMetadataResources metadata = resources.openMetadata(location)) {
            if (metadata == null) {
                return null;
            }
            IoSupplier<InputStream> supplier = metadata.getRootResource("pack.mcmeta");
            if (supplier == null) {
                return null;
            }
            try (InputStream in = supplier.get()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (RuntimeException | IOException e) {
            // A broken supplier must never escape into the pack-loading path:
            // treat it as "no legacy metadata" and leave the vanilla result.
            return null;
        }
    }
}
