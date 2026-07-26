package com.eventledger.gateway.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;

@Component
public class StructuredLogger {
    private static final Logger logger = LoggerFactory.getLogger(StructuredLogger.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public void info(String traceId, String serviceName, String message, Map<String, Object> context) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", System.currentTimeMillis());
        logEntry.put("level", "INFO");
        logEntry.put("traceId", traceId);
        logEntry.put("service", serviceName);
        logEntry.put("message", message);
        logEntry.putAll(context);
        
        try {
            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (Exception e) {
            logger.error("Failed to log structured entry", e);
        }
    }
    
    public void error(String traceId, String serviceName, String message, Throwable throwable, Map<String, Object> context) {
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", System.currentTimeMillis());
        logEntry.put("level", "ERROR");
        logEntry.put("traceId", traceId);
        logEntry.put("service", serviceName);
        logEntry.put("message", message);
        logEntry.put("exception", throwable.getMessage());
        logEntry.putAll(context);
        
        try {
            logger.error(objectMapper.writeValueAsString(logEntry), throwable);
        } catch (Exception e) {
            logger.error("Failed to log structured entry", e);
        }
    }
}
