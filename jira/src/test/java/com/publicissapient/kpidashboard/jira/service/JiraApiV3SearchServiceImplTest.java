/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.jira.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.jira.constant.JiraConstants;
import com.publicissapient.kpidashboard.jira.exception.JiraApiException;
import com.publicissapient.kpidashboard.jira.model.JiraSearchResponse;
import com.publicissapient.kpidashboard.jira.model.JiraToolConfig;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;

@RunWith(MockitoJUnitRunner.class)
public class JiraApiV3SearchServiceImplTest {

    @Mock
    private JiraCommonService jiraCommonService;

    @InjectMocks
    private JiraApiV3SearchServiceImpl jiraApiV3SearchService;

    private JiraToolConfig jiraToolConfig;
    private Connection connection;

    @Before
    public void setUp() {
        connection = new Connection();
        connection.setBaseUrl("https://test.atlassian.net");
        connection.setUsername("testuser");
        connection.setPassword("testpass");
        connection.setBearerToken(false);

        jiraToolConfig = JiraToolConfig.builder()
                .connection(Optional.of(connection))
                .build();
    }

    @Test
    public void testSearchJql_ShortJql_Success() throws Exception {
        String shortJql = "project = TEST";
        Set<String> fields = Set.of("summary", "status");
        
        HttpResponse<JsonNode> mockResponse = mock(HttpResponse.class);
        JsonNode mockJsonNode = mock(JsonNode.class);
        kong.unirest.json.JSONObject mockJsonObject = mock(kong.unirest.json.JSONObject.class);
        
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(mockJsonNode);
        when(mockJsonNode.getObject()).thenReturn(mockJsonObject);
        when(mockJsonObject.toString()).thenReturn("{\"issues\":[],\"total\":0}");
        when(jiraCommonService.decryptJiraPassword("testpass")).thenReturn("decryptedpass");

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class)) {
            kong.unirest.GetRequest getRequest = mock(kong.unirest.GetRequest.class);
            unirestMock.when(() -> Unirest.get(anyString())).thenReturn(getRequest);
            
            // Mock the entire fluent chain
            when(getRequest.basicAuth(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.header(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyBoolean())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyInt())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), any(Integer.class))).thenReturn(getRequest);
            when(getRequest.asJson()).thenReturn(mockResponse);

            JiraSearchResponse result = jiraApiV3SearchService.searchJql(shortJql, 50, fields, "test", jiraToolConfig);
            
            assertNotNull(result);
        }
    }

    @Test
    public void testSearchJql_LongJql_Success() throws Exception {
        String longJql = "a".repeat(JiraConstants.MAX_JQL_LENGTH_FOR_HTTP_GET + 1);
        
        HttpResponse<JsonNode> mockResponse = mock(HttpResponse.class);
        JsonNode mockJsonNode = mock(JsonNode.class);
        kong.unirest.json.JSONObject mockJsonObject = mock(kong.unirest.json.JSONObject.class);
        
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(mockJsonNode);
        when(mockJsonNode.getObject()).thenReturn(mockJsonObject);
        when(mockJsonObject.toString()).thenReturn("{\"issues\":[],\"total\":0}");
        when(jiraCommonService.decryptJiraPassword("testpass")).thenReturn("decryptedpass");

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class)) {
            kong.unirest.HttpRequestWithBody postRequest = mock(kong.unirest.HttpRequestWithBody.class);
            kong.unirest.RequestBodyEntity bodyEntity = mock(kong.unirest.RequestBodyEntity.class);
            
            unirestMock.when(() -> Unirest.post(anyString())).thenReturn(postRequest);
            when(postRequest.basicAuth(anyString(), anyString())).thenReturn(postRequest);
            when(postRequest.header(anyString(), anyString())).thenReturn(postRequest);
            when(postRequest.body(anyString())).thenReturn(bodyEntity);
            when(bodyEntity.asJson()).thenReturn(mockResponse);

            JiraSearchResponse result = jiraApiV3SearchService.searchJql(longJql, 50, null, null, jiraToolConfig);
            
            assertNotNull(result);
        }
    }

    @Test
    public void testSearchJql_NoConnection_ThrowsException() {
        jiraToolConfig = JiraToolConfig.builder()
                .connection(Optional.empty())
                .build();

        assertThrows(JiraApiException.class, () -> 
            jiraApiV3SearchService.searchJql("project = TEST", 50, null, null, jiraToolConfig));
    }

    @Test
    public void testSearchJql_BearerToken() throws Exception {
        connection.setBearerToken(true);
        connection.setPatOAuthToken("oauthtoken");
        
        String jql = "project = TEST";
        HttpResponse<JsonNode> mockResponse = mock(HttpResponse.class);
        JsonNode mockJsonNode = mock(JsonNode.class);
        kong.unirest.json.JSONObject mockJsonObject = mock(kong.unirest.json.JSONObject.class);
        
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(mockJsonNode);
        when(mockJsonNode.getObject()).thenReturn(mockJsonObject);
        when(mockJsonObject.toString()).thenReturn("{\"issues\":[],\"total\":0}");
        when(jiraCommonService.decryptJiraPassword("oauthtoken")).thenReturn("decryptedtoken");

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class)) {
            kong.unirest.GetRequest getRequest = mock(kong.unirest.GetRequest.class);
            unirestMock.when(() -> Unirest.get(anyString())).thenReturn(getRequest);
            when(getRequest.basicAuth(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.header(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyBoolean())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyInt())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), any(Integer.class))).thenReturn(getRequest);
            when(getRequest.asJson()).thenReturn(mockResponse);

            JiraSearchResponse result = jiraApiV3SearchService.searchJql(jql, 50, Set.of("summary", "status"), "test", jiraToolConfig);
            
            assertNotNull(result);
        }
    }

    @Test
    public void testSearchJql_HttpError() throws Exception {
        String jql = "project = TEST";
        HttpResponse<JsonNode> mockResponse = mock(HttpResponse.class);
        
        when(mockResponse.getStatus()).thenReturn(401);
        when(jiraCommonService.decryptJiraPassword("testpass")).thenReturn("decryptedpass");

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class)) {
            kong.unirest.GetRequest getRequest = mock(kong.unirest.GetRequest.class);
            unirestMock.when(() -> Unirest.get(anyString())).thenReturn(getRequest);
            when(getRequest.basicAuth(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.header(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyBoolean())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyInt())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), any(Integer.class))).thenReturn(getRequest);
            when(getRequest.asJson()).thenReturn(mockResponse);

            assertThrows(JiraApiException.class, () -> 
                jiraApiV3SearchService.searchJql(jql, 50, Set.of("summary", "status"), "test", jiraToolConfig));
        }
    }

    @Test
    public void testSearchJql_PostHttpError() throws Exception {
        String longJql = "a".repeat(JiraConstants.MAX_JQL_LENGTH_FOR_HTTP_GET + 1);
        
        HttpResponse<JsonNode> mockResponse = mock(HttpResponse.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(jiraCommonService.decryptJiraPassword("testpass")).thenReturn("decryptedpass");

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class)) {
            kong.unirest.HttpRequestWithBody postRequest = mock(kong.unirest.HttpRequestWithBody.class);
            kong.unirest.RequestBodyEntity bodyEntity = mock(kong.unirest.RequestBodyEntity.class);
            
            unirestMock.when(() -> Unirest.post(anyString())).thenReturn(postRequest);
            when(postRequest.basicAuth(anyString(), anyString())).thenReturn(postRequest);
            when(postRequest.header(anyString(), anyString())).thenReturn(postRequest);
            when(postRequest.body(anyString())).thenReturn(bodyEntity);
            when(bodyEntity.asJson()).thenReturn(mockResponse);

            assertThrows(JiraApiException.class, () -> 
                jiraApiV3SearchService.searchJql(longJql, 50, null, null, jiraToolConfig));
        }
    }

    @Test
    public void testSearchJql_NullJql() throws Exception {
        HttpResponse<JsonNode> mockResponse = mock(HttpResponse.class);
        JsonNode mockJsonNode = mock(JsonNode.class);
        kong.unirest.json.JSONObject mockJsonObject = mock(kong.unirest.json.JSONObject.class);
        
        when(mockResponse.getStatus()).thenReturn(200);
        when(mockResponse.getBody()).thenReturn(mockJsonNode);
        when(mockJsonNode.getObject()).thenReturn(mockJsonObject);
        when(mockJsonObject.toString()).thenReturn("{\"issues\":[],\"total\":0}");
        when(jiraCommonService.decryptJiraPassword("testpass")).thenReturn("decryptedpass");

        try (MockedStatic<Unirest> unirestMock = mockStatic(Unirest.class)) {
            kong.unirest.GetRequest getRequest = mock(kong.unirest.GetRequest.class);
            unirestMock.when(() -> Unirest.get(anyString())).thenReturn(getRequest);
            when(getRequest.basicAuth(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.header(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyString())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyBoolean())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), anyInt())).thenReturn(getRequest);
            when(getRequest.queryString(anyString(), any(Integer.class))).thenReturn(getRequest);
            when(getRequest.asJson()).thenReturn(mockResponse);
            JiraSearchResponse result = jiraApiV3SearchService.searchJql("In [Story]", 50, Set.of("summary", "status"), "test", jiraToolConfig);
            
            assertNotNull(result);
        }
    }

}