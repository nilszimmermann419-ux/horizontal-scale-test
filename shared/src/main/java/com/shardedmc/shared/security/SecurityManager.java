package com.shardedmc.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SecurityManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityManager.class);
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    private final ConcurrentHashMap<String, TokenInfo> activeTokens = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] encryptionKey;
    
    public SecurityManager(String base64Key) {
        this.encryptionKey = Base64.getDecoder().decode(base64Key);
    }
    
    public SecurityManager() {
        byte[] key = new byte[32];
        secureRandom.nextBytes(key);
        this.encryptionKey = key;
    }
    
    // Token management for inter-service authentication
    public String generateToken(String serviceId, long durationMinutes) {
        String token = generateSecureToken();
        Instant expiry = Instant.now().plusSeconds(durationMinutes * 60);
        activeTokens.put(token, new TokenInfo(serviceId, expiry));
        LOGGER.debug("Generated token for service: {}, expires: {}", serviceId, expiry);
        return token;
    }
    
    public boolean validateToken(String token, String expectedServiceId) {
        TokenInfo info = activeTokens.get(token);
        if (info == null) {
            return false;
        }
        
        if (info.expiry.isBefore(Instant.now())) {
            activeTokens.remove(token);
            return false;
        }
        
        return info.serviceId.equals(expectedServiceId);
    }
    
    public void revokeToken(String token) {
        activeTokens.remove(token);
        LOGGER.debug("Revoked token");
    }
    
    public void cleanupExpiredTokens() {
        Instant now = Instant.now();
        activeTokens.entrySet().removeIf(entry -> entry.getValue().expiry.isBefore(now));
    }
    
    // Encryption for sensitive data
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encrypted.length);
            byteBuffer.put(iv);
            byteBuffer.put(encrypted);
            
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            LOGGER.error("Encryption failed", e);
            throw new SecurityException("Encryption failed", e);
        }
    }
    
    public String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Decryption failed", e);
            throw new SecurityException("Decryption failed", e);
        }
    }
    
    // Hashing for passwords/secrets
    public String hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("Hashing failed", e);
            throw new SecurityException("Hashing failed", e);
        }
    }
    
    public String hashWithSalt(String input, String salt) {
        return hash(input + salt);
    }
    
    // Secure token generation
    private String generateSecureToken() {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
    
    // Rate limiting helpers
    private final ConcurrentHashMap<String, RateLimitInfo> rateLimits = new ConcurrentHashMap<>();
    
    public boolean checkRateLimit(String key, int maxRequests, long windowSeconds) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);
        
        RateLimitInfo info = rateLimits.computeIfAbsent(key, k -> new RateLimitInfo());
        
        synchronized (info) {
            // Remove old entries
            info.requests.removeIf(time -> time.isBefore(windowStart));
            
            if (info.requests.size() >= maxRequests) {
                return false;
            }
            
            info.requests.add(now);
            return true;
        }
    }
    
    public void resetRateLimit(String key) {
        rateLimits.remove(key);
    }
    
    private static class TokenInfo {
        final String serviceId;
        final Instant expiry;
        
        TokenInfo(String serviceId, Instant expiry) {
            this.serviceId = serviceId;
            this.expiry = expiry;
        }
    }
    
    private static class RateLimitInfo {
        final java.util.List<Instant> requests = new java.util.ArrayList<>();
    }
}