package com.shardedmc.plugin;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class PluginClassLoader extends URLClassLoader {
    
    private final Path pluginPath;
    private final JarFile jarFile;
    
    public PluginClassLoader(Path pluginPath, ClassLoader parent) throws IOException {
        super(new URL[]{pluginPath.toUri().toURL()}, parent);
        this.pluginPath = pluginPath;
        this.jarFile = new JarFile(pluginPath.toFile());
    }
    
    public Path getPluginPath() {
        return pluginPath;
    }
    
    public JarFile getJarFile() {
        return jarFile;
    }
    
    @Override
    public void close() throws IOException {
        jarFile.close();
        super.close();
    }
}
