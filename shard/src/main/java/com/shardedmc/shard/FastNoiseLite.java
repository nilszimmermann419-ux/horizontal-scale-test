package com.shardedmc.shard;

/**
 * FastNoiseLite implementation for world generation.
 * Based on the FastNoiseLite library by Auburn (MIT License).
 */
public class FastNoiseLite {
    
    private int mSeed = 1337;
    private float mFrequency = 0.01f;
    private NoiseType mNoiseType = NoiseType.OpenSimplex2;
    private FractalType mFractalType = FractalType.None;
    private int mOctaves = 3;
    private float mLacunarity = 2.0f;
    private float mGain = 0.5f;
    private float mWeightedStrength = 0.0f;
    private float mPingPongStrength = 2.0f;
    
    public FastNoiseLite(int seed) {
        this.mSeed = seed;
    }
    
    public void SetNoiseType(NoiseType noiseType) {
        this.mNoiseType = noiseType;
    }
    
    public void SetFrequency(float frequency) {
        this.mFrequency = frequency;
    }
    
    public void SetFractalType(FractalType fractalType) {
        this.mFractalType = fractalType;
    }
    
    public void SetFractalOctaves(int octaves) {
        this.mOctaves = octaves;
    }
    
    public enum NoiseType {
        OpenSimplex2
    }
    
    public enum FractalType {
        None,
        FBm
    }
    
    public float GetNoise(float x, float y) {
        float freq = mFrequency;
        float amp = 1.0f;
        float max = 1.0f;
        float total = noise2D(x * freq, y * freq, mSeed);
        
        if (mFractalType == FractalType.FBm) {
            for (int i = 1; i < mOctaves; i++) {
                freq *= mLacunarity;
                amp *= mGain;
                max += amp;
                total += noise2D(x * freq, y * freq, mSeed + i * 100) * amp;
            }
        }
        
        return total / max;
    }
    
    public float GetNoise(float x, float y, float z) {
        float freq = mFrequency;
        float amp = 1.0f;
        float max = 1.0f;
        float total = noise3D(x * freq, y * freq, z * freq, mSeed);
        
        if (mFractalType == FractalType.FBm) {
            for (int i = 1; i < mOctaves; i++) {
                freq *= mLacunarity;
                amp *= mGain;
                max += amp;
                total += noise3D(x * freq, y * freq, z * freq, mSeed + i * 100) * amp;
            }
        }
        
        return total / max;
    }
    
    // Simple OpenSimplex2-style noise implementation
    private float noise2D(float x, float y, int seed) {
        // Simple gradient noise
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float xf = x - x0;
        float yf = y - y0;
        
        // Hash coordinates
        int hash00 = hash(x0, y0, seed);
        int hash10 = hash(x0 + 1, y0, seed);
        int hash01 = hash(x0, y0 + 1, seed);
        int hash11 = hash(x0 + 1, y0 + 1, seed);
        
        // Gradients
        float g00 = grad(hash00, xf, yf);
        float g10 = grad(hash10, xf - 1, yf);
        float g01 = grad(hash01, xf, yf - 1);
        float g11 = grad(hash11, xf - 1, yf - 1);
        
        // Smooth interpolation
        float u = fade(xf);
        float v = fade(yf);
        
        float x00 = lerp(g00, g10, u);
        float x01 = lerp(g01, g11, u);
        
        return lerp(x00, x01, v);
    }
    
    private float noise3D(float x, float y, float z, int seed) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        float xf = x - x0;
        float yf = y - y0;
        float zf = z - z0;
        
        float u = fade(xf);
        float v = fade(yf);
        float w = fade(zf);
        
        // 8 corners
        float n000 = grad(hash(x0, y0, z0, seed), xf, yf, zf);
        float n100 = grad(hash(x0 + 1, y0, z0, seed), xf - 1, yf, zf);
        float n010 = grad(hash(x0, y0 + 1, z0, seed), xf, yf - 1, zf);
        float n110 = grad(hash(x0 + 1, y0 + 1, z0, seed), xf - 1, yf - 1, zf);
        float n001 = grad(hash(x0, y0, z0 + 1, seed), xf, yf, zf - 1);
        float n101 = grad(hash(x0 + 1, y0, z0 + 1, seed), xf - 1, yf, zf - 1);
        float n011 = grad(hash(x0, y0 + 1, z0 + 1, seed), xf, yf - 1, zf - 1);
        float n111 = grad(hash(x0 + 1, y0 + 1, z0 + 1, seed), xf - 1, yf - 1, zf - 1);
        
        float nx00 = lerp(n000, n100, u);
        float nx10 = lerp(n010, n110, u);
        float nx01 = lerp(n001, n101, u);
        float nx11 = lerp(n011, n111, u);
        
        float nxy0 = lerp(nx00, nx10, v);
        float nxy1 = lerp(nx01, nx11, v);
        
        return lerp(nxy0, nxy1, w);
    }
    
    private int hash(int x, int y, int seed) {
        int h = seed;
        h ^= x * 374761393;
        h ^= y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }
    
    private int hash(int x, int y, int z, int seed) {
        int h = seed;
        h ^= x * 374761393;
        h ^= y * 668265263;
        h ^= z * 2147483647;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }
    
    private float grad(int hash, float x, float y) {
        int h = hash & 0x3;
        float u = h < 2 ? x : y;
        float v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    private float grad(int hash, float x, float y, float z) {
        int h = hash & 15;
        float u = h < 8 ? x : y;
        float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    private float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    private float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
}
