package com.publicissapient.kpidashboard.rally.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.repository.connection.ConnectionRepository;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.RallyTypeDefinitionResponse;

import lombok.extern.slf4j.Slf4j;
/**
 * @author girpatha
 */
@Component
@Slf4j
public class RallyRestClient {
    private static final String BASE_URL = "https://rally1.rallydev.com/slm/webservice/v2.0";
    private static final String API_KEY_HEADER = "zsessionid";
    private static final String CONTENT_TYPE = "application/json";

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private RestTemplate restTemplate;

    public RallyRestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getBaseUrl() {
        return BASE_URL;
    }

    private HttpHeaders createHeaders(ProjectConfFieldMapping projectConfig) {
        HttpHeaders headers = new HttpHeaders();
        Connection connection = getConnection(projectConfig);
        if (connection != null && connection.getAccessToken() != null) {
            headers.set(API_KEY_HEADER, connection.getAccessToken());
            headers.set("Accept", CONTENT_TYPE);
            headers.set("Content-Type", CONTENT_TYPE);
        }
        return headers;
    }

    private Connection getConnection(ProjectConfFieldMapping projectConfig) {
        if (projectConfig == null) {
            log.error("ProjectConfFieldMapping is null");
            return null;
        }
        
        if (projectConfig.getProjectToolConfig() == null) {
            log.error("ProjectToolConfig is null for project: {}", 
                    projectConfig.getProjectBasicConfig() != null ? 
                    projectConfig.getProjectBasicConfig().getProjectName() : "unknown");
            return null;
        }
        
        if (projectConfig.getProjectToolConfig().getConnectionId() != null) {
            return connectionRepository.findById(projectConfig.getProjectToolConfig().getConnectionId()).orElse(null);
        }
        return null;
    }

    private <T> T parseResponse(String responseBody, Class<T> responseType) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        T parsedResponse = objectMapper.readValue(responseBody, responseType);
        if (parsedResponse instanceof RallyTypeDefinitionResponse) {
            RallyTypeDefinitionResponse response = (RallyTypeDefinitionResponse) parsedResponse;
            if (response.getQueryResult() != null && !response.getQueryResult().getErrors().isEmpty()) {
                log.error("Rally API returned errors: {}", response.getQueryResult().getErrors());
                throw new RuntimeException("Rally API returned errors: " + response.getQueryResult().getErrors()); // NOSONAR
            }
        }
        return parsedResponse;
    }

    public <T> ResponseEntity<T> get(String url, ProjectConfFieldMapping projectConfig, Class<T> responseType) throws JsonProcessingException {
        try {
            HttpHeaders headers = createHeaders(projectConfig);
            if (headers.isEmpty()) {
                log.error("No access token found for connection. ProjectToolConfig may be null or missing connectionId");
                return null;
            }

            log.debug("Making Rally API request to URL: {} with headers: {}", url, headers);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> rawResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (rawResponse.getBody() != null) {
                String responseBody = rawResponse.getBody();
                // Log the full response for detailed debugging
                log.info("Raw Rally API response for URL {}: {}", url, responseBody);
                
                T parsedResponse = parseResponse(responseBody, responseType);
                log.info("Successfully parsed Rally API response to type: {}", responseType.getSimpleName());
                return ResponseEntity.ok(parsedResponse);
            } else {
                log.warn("Received null response or body from Rally API");
                return null;
            }
        } catch (Exception e) {
            log.error("Error making Rally API request to URL: " + url, e);
            throw e;
        }
    }
}
