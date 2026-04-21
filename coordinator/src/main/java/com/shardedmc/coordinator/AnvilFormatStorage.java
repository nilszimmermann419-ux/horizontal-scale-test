package com.shardedmc.coordinator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Stores world data in Anvil format (.mca files).
 * This ensures compatibility with standard Minecraft worlds.
 * Each region file contains 32x32 chunks.
 * 
 * Uses raw byte arrays instead of Minestom Chunk objects to avoid dependency.
 */
public class AnvilFormatStorage {
    private static final Logger logger = LoggerFactory.getLogger(AnvilFormatStorage.class);
    
    private final Path worldPath;
    private final Path regionPath;
    
    // Region file cache with size limit
    private static final int MAX_CACHED_REGIONS = 256;
    private final Map<String, RegionFile> regionCache = new ConcurrentHashMap<>();
    
    public AnvilFormatStorage(String worldName) {
        this.worldPath = Paths.get("worlds", worldName);
        this.regionPath = worldPath.resolve("region");
        
        try {
            Files.createDirectories(regionPath);
            logger.info("Anvil storage initialized at {}", regionPath);
        } catch (IOException e) {
            logger.error("Failed to create world directory", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Save chunk data to disk.
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param data Raw chunk data bytes
     */
    public CompletableFuture<Void> saveChunk(int chunkX, int chunkZ, byte[] data) {
        return CompletableFuture.runAsync(() -> {
            try {
                int regionX = chunkX >> 5;
                int regionZ = chunkZ >> 5;
                
                RegionFile regionFile = getRegionFile(regionX, regionZ);
                regionFile.writeChunk(chunkX & 31, chunkZ & 31, data);
                
                logger.debug("Saved chunk {},{} to region {}, {}", 
                        chunkX, chunkZ, regionX, regionZ);
            } catch (Exception e) {
                logger.error("Failed to save chunk {},{}: {}", 
                        chunkX, chunkZ, e.getMessage());
            }
        });
    }
    
    /**
     * Load chunk data from disk.
     * @return Chunk data bytes, or null if chunk doesn't exist
     */
    public CompletableFuture<byte[]> loadChunk(int chunkX, int chunkZ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int regionX = chunkX >> 5;
                int regionZ = chunkZ >> 5;
                
                RegionFile regionFile = getRegionFile(regionX, regionZ);
                return regionFile.readChunk(chunkX & 31, chunkZ & 31);
            } catch (Exception e) {
                logger.error("Failed to load chunk {},{}: {}", chunkX, chunkZ, e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * Check if a chunk exists on disk.
     */
    public boolean chunkExists(int chunkX, int chunkZ) {
        try {
            int regionX = chunkX >> 5;
            int regionZ = chunkZ >> 5;
            
            Path regionFile = regionPath.resolve(String.format("r.%d.%d.mca", regionX, regionZ));
            if (!Files.exists(regionFile)) {
                return false;
            }
            
            RegionFile region = getRegionFile(regionX, regionZ);
            return region.hasChunk(chunkX & 31, chunkZ & 31);
        } catch (Exception e) {
            return false;
        }
    }
    
    private RegionFile getRegionFile(int regionX, int regionZ) throws IOException {
        String key = regionX + "." + regionZ;
        // Evict oldest entries if cache is full
        if (regionCache.size() >= MAX_CACHED_REGIONS && !regionCache.containsKey(key)) {
            Iterator<Map.Entry<String, RegionFile>> it = regionCache.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<String, RegionFile> oldest = it.next();
                try {
                    oldest.getValue().close();
                } catch (IOException e) {
                    logger.warn("Failed to close region file", e);
                }
                it.remove();
            }
        }
        return regionCache.computeIfAbsent(key, k -> {
            try {
                Path file = regionPath.resolve(String.format("r.%d.%d.mca", regionX, regionZ));
                return new RegionFile(file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Simplified RegionFile implementation for Anvil format.
     */
    private static class RegionFile {
        private final Path file;
        private final RandomAccessFile raf;
        
        // Offset table: 4 bytes per chunk
        private final int[] offsetTable = new int[1024];
        
        // Timestamp table: 4 bytes per chunk
        private final int[] timestampTable = new int[1024];
        
        RegionFile(Path file) throws IOException {
            this.file = file;
            if (Files.exists(file)) {
                this.raf = new RandomAccessFile(file.toFile(), "rw");
                readHeaders();
            } else {
                this.raf = new RandomAccessFile(file.toFile(), "rw");
                initializeFile();
            }
        }
        
        void close() throws IOException {
            raf.close();
        }
        
        private void readHeaders() throws IOException {
            raf.seek(0);
            for (int i = 0; i < 1024; i++) {
                offsetTable[i] = raf.readInt();
            }
            for (int i = 0; i < 1024; i++) {
                timestampTable[i] = raf.readInt();
            }
        }
        
        private void initializeFile() throws IOException {
            for (int i = 0; i < 1024; i++) {
                raf.writeInt(0);
            }
            for (int i = 0; i < 1024; i++) {
                raf.writeInt(0);
            }
        }
        
        boolean hasChunk(int chunkX, int chunkZ) {
            int index = chunkX + chunkZ * 32;
            return offsetTable[index] != 0;
        }
        
        void writeChunk(int chunkX, int chunkZ, byte[] data) throws IOException {
            // Compress data
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_SPEED))) {
                dos.write(data);
            }
            byte[] compressedData = baos.toByteArray();
            
            int index = chunkX + chunkZ * 32;
            
            // Calculate sectors needed (each sector is 4096 bytes)
            int sectorsNeeded = (compressedData.length + 4 + 4095) / 4096;
            
            // Find or allocate space
            int offset = findFreeSpace(sectorsNeeded);
            if (offset == -1) {
                // Append to end of file
                offset = (int) ((raf.length() + 4095) / 4096);
            }
            
            // Write data
            raf.seek(offset * 4096L);
            raf.writeInt(compressedData.length + 1);
            raf.write(2); // Compression type: 2 = zlib
            raf.write(compressedData);
            
            // Pad to sector boundary
            long end = raf.getFilePointer();
            long padding = (4096 - (end % 4096)) % 4096;
            for (int i = 0; i < padding; i++) {
                raf.write(0);
            }
            
            // Update offset table
            offsetTable[index] = (offset << 8) | sectorsNeeded;
            timestampTable[index] = (int) (System.currentTimeMillis() / 1000);
            
            // Write updated headers
            raf.seek((index) * 4L);
            raf.writeInt(offsetTable[index]);
            raf.seek((1024 + index) * 4L);
            raf.writeInt(timestampTable[index]);
        }
        
        byte[] readChunk(int chunkX, int chunkZ) throws IOException {
            int index = chunkX + chunkZ * 32;
            int offset = offsetTable[index] >> 8;
            int sectors = offsetTable[index] & 0xFF;
            
            if (offset == 0) {
                return null;
            }
            
            raf.seek(offset * 4096L);
            int length = raf.readInt();
            int compressionType = raf.read();
            
            byte[] compressedData = new byte[length - 1];
            raf.readFully(compressedData);
            
            // Decompress
            ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
            try (InflaterInputStream iis = new InflaterInputStream(bais)) {
                return iis.readAllBytes();
            }
        }
        
        private int findFreeSpace(int sectorsNeeded) {
            return -1; // Simple implementation: append to end
        }
    }
}
