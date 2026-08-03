package dev.heitorrosa.packconverter.pack;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;

/**
 * {@link Pack.ResourcesSupplier} that wraps every opened {@link PackResources}
 * in a {@link ConverterPackResources}, so a legacy pack's resources are served
 * under modern names.
 */
public final class ConverterResourcesSupplier implements Pack.ResourcesSupplier {

    private final Pack.ResourcesSupplier delegate;

    public ConverterResourcesSupplier(Pack.ResourcesSupplier delegate) {
        this.delegate = delegate;
    }

    @Override
    public PackResources openPrimary(PackLocationInfo location) {
        return new ConverterPackResources(delegate.openPrimary(location));
    }

    @Override
    public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
        return new ConverterPackResources(delegate.openFull(location, metadata));
    }
}
