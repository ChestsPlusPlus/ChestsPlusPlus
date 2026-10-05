package com.jamesdpeters.chestsplusplus;

import com.jamesdpeters.chestsplusplus.core.Resources;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/** Downloads the libraries listed in {@code paper-libraries.txt} (written by the build) from Paper's Maven Central mirror. */
public final class ChestsPlusPlusLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        resolver.addRepository(new RemoteRepository.Builder("central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());
        Resources.text("paper-libraries.txt")
                .lines()
                .filter(line -> !line.isBlank())
                .forEach(coordinates -> resolver.addDependency(new Dependency(new DefaultArtifact(coordinates), null)));
        classpathBuilder.addLibrary(resolver);
    }
}
