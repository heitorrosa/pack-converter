package dev.heitorrosa.packconverter.pack;

import java.util.stream.Stream;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
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
    public PackMetadataResources openMetadata(PackLocationInfo location) {
        return delegate.openMetadata(location);
    }

    @Override
    public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata metadata) {
        return delegate.openResources(location, metadata).map(ConverterPackResources::new);
    }
}
