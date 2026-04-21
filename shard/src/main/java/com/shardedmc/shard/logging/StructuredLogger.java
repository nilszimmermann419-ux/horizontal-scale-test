package com.shardedmc.shard.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

public class StructuredLogger {
    private final Logger logger;
    private final ObjectMapper objectMapper;

    private String shardId;
    private String playerId;
    private String region;
    private String eventType;

    public StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.objectMapper = new ObjectMapper();
    }

    public StructuredLogger withShardId(String shardId) {
        this.shardId = shardId;
        return this;
    }

    public StructuredLogger withPlayerId(String playerId) {
        this.playerId = playerId;
        return this;
    }

    public StructuredLogger withRegion(String region) {
        this.region = region;
        return this;
    }

    public StructuredLogger withEventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    private void setMdc() {
        if (shardId != null) MDC.put("shard_id", shardId);
        if (playerId != null) MDC.put("player_id", playerId);
        if (region != null) MDC.put("region", region);
        if (eventType != null) MDC.put("event_type", eventType);
    }

    private void clearMdc() {
        MDC.remove("shard_id");
        MDC.remove("player_id");
        MDC.remove("region");
        MDC.remove("event_type");
    }

    private String formatMessage(String level, String message, Map<String, Object> additionalFields) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", System.currentTimeMillis());
        logEntry.put("level", level);
        logEntry.put("message", message);
        logEntry.put("logger", logger.getName());

        if (shardId != null) logEntry.put("shard_id", shardId);
        if (playerId != null) logEntry.put("player_id", playerId);
        if (region != null) logEntry.put("region", region);
        if (eventType != null) logEntry.put("event_type", eventType);

        if (additionalFields != null) {
            logEntry.putAll(additionalFields);
        }

        try {
            return objectMapper.writeValueAsString(logEntry);
        } catch (JsonProcessingException e) {
            return String.format("{\"timestamp\":%d,\"level\":\"%s\",\"message\":\"%s\",\"error\":\"json_serialization_failed\"}",
                    System.currentTimeMillis(), level, message);
        }
    }

    public void debug(String message) {
        debug(message, null);
    }

    public void debug(String message, Map<String, Object> fields) {
        if (logger.isDebugEnabled()) {
            setMdc();
            logger.debug(formatMessage("DEBUG", message, fields));
            clearMdc();
        }
    }

    public void info(String message) {
        info(message, null);
    }

    public void info(String message, Map<String, Object> fields) {
        if (logger.isInfoEnabled()) {
            setMdc();
            logger.info(formatMessage("INFO", message, fields));
            clearMdc();
        }
    }

    public void warn(String message) {
        warn(message, null);
    }

    public void warn(String message, Map<String, Object> fields) {
        if (logger.isWarnEnabled()) {
            setMdc();
            logger.warn(formatMessage("WARN", message, fields));
            clearMdc();
        }
    }

    public void warn(String message, Throwable throwable) {
        warn(message, throwable, null);
    }

    public void warn(String message, Throwable throwable, Map<String, Object> fields) {
        if (logger.isWarnEnabled()) {
            setMdc();
            Map<String, Object> logFields = fields != null ? new HashMap<>(fields) : new HashMap<>();
            logFields.put("exception", throwable != null ? throwable.getMessage() : "null");
            logger.warn(formatMessage("WARN", message, logFields));
            clearMdc();
        }
    }

    public void error(String message) {
        error(message, null);
    }

    public void error(String message, Map<String, Object> fields) {
        if (logger.isErrorEnabled()) {
            setMdc();
            logger.error(formatMessage("ERROR", message, fields));
            clearMdc();
        }
    }

    public void error(String message, Throwable throwable) {
        error(message, throwable, null);
    }

    public void error(String message, Throwable throwable, Map<String, Object> fields) {
        if (logger.isErrorEnabled()) {
            setMdc();
            Map<String, Object> logFields = fields != null ? new HashMap<>(fields) : new HashMap<>();
            logFields.put("exception", throwable != null ? throwable.getMessage() : "null");
            logFields.put("exception_type", throwable != null ? throwable.getClass().getName() : "null");
            logger.error(formatMessage("ERROR", message, logFields));
            clearMdc();
        }
    }
}
