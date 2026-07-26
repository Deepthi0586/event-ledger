package com.eventledger.gateway.util;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.UUID;

@Configuration
public class TracingConfig {
    
    @Bean
    public Tracer tracer() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
            .build();
        
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build()
            .getTracerProvider();
        
        return tracerProvider.get("event-gateway");
    }
    
    @Bean
    public TraceIdGenerator traceIdGenerator() {
        return new TraceIdGenerator();
    }
    
    public static class TraceIdGenerator {
        public String generate() {
            return UUID.randomUUID().toString();
        }
    }
}
