package com.agil.energy.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))  // prediction calls can be slow
                .build();
    }

    /**
     * LLM-facing RestTemplate — generation on CPU is slow, and RAG chat adds
     * retrieval plus a long prompt on top. Three minutes is enough for a cold
     * model load without leaving the UI hanging indefinitely.
     * Named bean — inject with @Qualifier("aiRestTemplate").
     */
    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofMinutes(3))
                .build();
    }

    /**
     * Long-running RestTemplate — used by batch operations that can legitimately
     * take several minutes (training 4 models × N station/fuel pairs).
     * Named bean — inject with @Qualifier("longRunningRestTemplate").
     */
    @Bean("longRunningRestTemplate")
    public RestTemplate longRunningRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofMinutes(15))   // enough for 100+ station batches
                .build();
    }
}