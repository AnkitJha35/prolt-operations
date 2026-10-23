package com.sense.prolt_operations.service;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import com.sense.prolt_operations.config.AppProperties;

@Service
public class LandingPriceService {

    private static final Logger log = LoggerFactory.getLogger(LandingPriceService.class);
    private final RestClient restClient;
    private final AppProperties appProperties;

    public LandingPriceService(
            AppProperties appProperties,
            @Value("${app.iex-calculator.connect-timeout:5000}") int connectTimeoutMs,
            @Value("${app.iex-calculator.read-timeout:15000}") int readTimeoutMs) {
        this.appProperties = appProperties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)")
            .build();
    }

    private String getBaseUrl() {
        String url = appProperties.getIexCalculatorApiUrl();
        if (url == null || url.isBlank()) {
            return "https://www.iexindia.com";
        }
        return url.replaceAll("/+$", "");
    }

    public Map<String, Object> calculate(Map<String, String> params) {
        String endpoint = getBaseUrl() + "/api/v1/calculator";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.get()
                .uri(endpoint, uriBuilder -> {
                    params.forEach(uriBuilder::queryParam);
                    return uriBuilder.build();
                })
                .retrieve()
                .body(Map.class);
            return response;
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            log.error("[LandingPriceService] IEX API error: status={}, body={}", e.getStatusCode().value(), body);
            throw new RuntimeException("IEX API Error: " + e.getStatusCode().value() + " - " + body);
        } catch (Exception e) {
            log.error("[LandingPriceService] Failed to calculate landing price from IEX API: {}", e.getMessage());
            throw new RuntimeException("Failed to calculate landing price from IEX API", e);
        }
    }

    public Object getConsumerCategories(String state) {
        String endpoint = getBaseUrl() + "/api/v1/calculator/consumer-category";
        try {
            return restClient.get()
                .uri(endpoint, uriBuilder -> uriBuilder.queryParam("state", state).build())
                .retrieve()
                .body(Object.class);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("IEX API Error: " + e.getStatusCode().value() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Failed to fetch consumer categories from IEX API", e);
        }
    }

    public Object getVoltages(String state, String consumerCategory) {
        String endpoint = getBaseUrl() + "/api/v1/calculator/voltage";
        try {
            return restClient.get()
                .uri(endpoint, uriBuilder -> uriBuilder
                    .queryParam("state", state)
                    .queryParam("consumerCategory", consumerCategory)
                    .build())
                .retrieve()
                .body(Object.class);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("IEX API Error: " + e.getStatusCode().value() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Failed to fetch voltages from IEX API", e);
        }
    }

    public Object getTodMonths(String state, String consumerCategory, String voltage) {
        String endpoint = getBaseUrl() + "/api/v1/calculator/tod-months";
        try {
            return restClient.get()
                .uri(endpoint, uriBuilder -> uriBuilder
                    .queryParam("state", state)
                    .queryParam("consumerCategory", consumerCategory)
                    .queryParam("voltage", voltage)
                    .build())
                .retrieve()
                .body(Object.class);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("IEX API Error: " + e.getStatusCode().value() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Failed to fetch TOD months from IEX API", e);
        }
    }

    public Object getTodSlots(String state, String consumerCategory, String voltage, String month) {
        String endpoint = getBaseUrl() + "/api/v1/calculator/tod-slots";
        try {
            return restClient.get()
                .uri(endpoint, uriBuilder -> uriBuilder
                    .queryParam("state", state)
                    .queryParam("consumerCategory", consumerCategory)
                    .queryParam("voltage", voltage)
                    .queryParam("month", month)
                    .build())
                .retrieve()
                .body(Object.class);
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("IEX API Error: " + e.getStatusCode().value() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage() != null ? e.getMessage() : "Failed to fetch TOD slots from IEX API", e);
        }
    }
}
