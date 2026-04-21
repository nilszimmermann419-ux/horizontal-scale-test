package com.shardedmc.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StructuredLogger {
    
    private final Logger logger;
    private final Map<String, String> defaultContext = new ConcurrentHashMap<>();
    
    public StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    public StructuredLogger(String name) {
        this.logger = LoggerFactory.getLogger(name);
    }
    
    public void addDefaultContext(String key, String value) {
        defaultContext.put(key, value);
    }
    
    public void removeDefaultContext(String key) {
        defaultContext.remove(key);
    }
    
    private java.util.Set<String> setupMDC(Map<String, String> additionalContext) {
        java.util.Set<String> keysAdded = new java.util.HashSet<>();
        defaultContext.forEach((key, value) -> {
            MDC.put(key, value);
            keysAdded.add(key);
        });
        if (additionalContext != null) {
            additionalContext.forEach((key, value) -> {
                MDC.put(key, value);
                keysAdded.add(key);
            });
        }
        return keysAdded;
    }
    
    private void clearMDC(java.util.Set<String> keysAdded) {
        keysAdded.forEach(MDC::remove);
    }
    
    public void info(String message) {
        info(message, (Map<String, String>) null);
    }
    
    public void info(String message, Map<String, String> context) {
        java.util.Set<String> keysAdded = setupMDC(context);
        try {
            logger.info(message);
        } finally {
            clearMDC(keysAdded);
        }
    }
    
    public void info(String message, Object... args) {
        logger.info(message, args);
    }
    
    public void debug(String message) {
        debug(message, (Map<String, String>) null);
    }
    
    public void debug(String message, Map<String, String> context) {
        java.util.Set<String> keysAdded = setupMDC(context);
        try {
            logger.debug(message);
        } finally {
            clearMDC(keysAdded);
        }
    }
    
    public void debug(String message, Object... args) {
        logger.debug(message, args);
    }
    
    public void warn(String message) {
        warn(message, (Map<String, String>) null);
    }
    
    public void warn(String message, Map<String, String> context) {
        java.util.Set<String> keysAdded = setupMDC(context);
        try {
            logger.warn(message);
        } finally {
            clearMDC(keysAdded);
        }
    }
    
    public void warn(String message, Throwable throwable) {
        logger.warn(message, throwable);
    }
    
    public void warn(String message, Throwable throwable, Map<String, String> context) {
        java.util.Set<String> keysAdded = setupMDC(context);
        try {
            logger.warn(message, throwable);
        } finally {
            clearMDC(keysAdded);
        }
    }
    
    public void error(String message) {
        error(message, (Map<String, String>) null);
    }
    
    public void error(String message, Map<String, String> context) {
        java.util.Set<String> keysAdded = setupMDC(context);
        try {
            logger.error(message);
        } finally {
            clearMDC(keysAdded);
        }
    }
    
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
    
    public void error(String message, Throwable throwable, Map<String, String> context) {
        java.util.Set<String> keysAdded = setupMDC(context);
        try {
            logger.error(message, throwable);
        } finally {
            clearMDC(keysAdded);
        }
    }
    
    // Structured logging with event type
    public void logEvent(String eventType, String message, Map<String, String> context) {
        Map<String, String> eventContext = new HashMap<>();
        eventContext.put("event_type", eventType);
        if (context != null) {
            eventContext.putAll(context);
        }
        info(message, eventContext);
    }
    
    public void logPlayerEvent(String playerId, String eventType, String message, Map<String, String> context) {
        Map<String, String> eventContext = new HashMap<>();
        eventContext.put("player_id", playerId);
        eventContext.put("event_type", eventType);
        if (context != null) {
            eventContext.putAll(context);
        }
        info(message, eventContext);
    }
    
    public void logShardEvent(String shardId, String eventType, String message, Map<String, String> context) {
        Map<String, String> eventContext = new HashMap<>();
        eventContext.put("shard_id", shardId);
        eventContext.put("event_type", eventType);
        if (context != null) {
            eventContext.putAll(context);
        }
        info(message, eventContext);
    }
    
    public void logError(String errorCode, String message, Throwable throwable, Map<String, String> context) {
        Map<String, String> errorContext = new HashMap<>();
        errorContext.put("error_code", errorCode);
        if (context != null) {
            errorContext.putAll(context);
        }
        error(message, throwable, errorContext);
    }
    
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }
    
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }
    
    public boolean isWarnEnabled() {
        return logger.isWarnEnabled();
    }
    
    public boolean isErrorEnabled() {
        return logger.isErrorEnabled();
    }
}