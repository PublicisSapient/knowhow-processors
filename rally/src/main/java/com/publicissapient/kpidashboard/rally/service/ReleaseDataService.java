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
package com.publicissapient.kpidashboard.rally.service;

import java.io.IOException;
import java.util.List;

import org.bson.types.ObjectId;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.ReleaseDataResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for handling Rally release data operations
 */
@Service
@Slf4j
public class ReleaseDataService {

    private static final String RALLY_TOOL_NAME = "Rally";

    private final FetchScrumReleaseData fetchScrumReleaseData;
    private final ProjectBasicConfigRepository projectBasicConfigRepository;
    private final ProjectToolConfigRepository projectToolConfigRepository;
    
    /**
     * Constructor for dependency injection
     * 
     * @param fetchScrumReleaseData Service for fetching scrum release data
     * @param projectBasicConfigRepository Repository for project basic configurations
     * @param projectToolConfigRepository Repository for project tool configurations
     */
    @Autowired
    public ReleaseDataService(FetchScrumReleaseData fetchScrumReleaseData,
                             ProjectBasicConfigRepository projectBasicConfigRepository,
                             ProjectToolConfigRepository projectToolConfigRepository) {
        this.fetchScrumReleaseData = fetchScrumReleaseData;
        this.projectBasicConfigRepository = projectBasicConfigRepository;
        this.projectToolConfigRepository = projectToolConfigRepository;
    }
    
    /**
     * Fetch and process release data for a project
     * 
     * @param projectId Project ID
     * @return ResponseEntity with ReleaseDataResponse containing success or error information
     */
    public ResponseEntity<ReleaseDataResponse> fetchAndProcessReleaseData(String projectId) {
        try {
            // Find project configuration by ID
            ProjectBasicConfig projectConfig = findProjectConfig(projectId);
            if (projectConfig == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ReleaseDataResponse.error("Project not found with ID: " + projectId));
            }
            
            // Find the Rally tool config for this project
            ProjectToolConfig toolConfig = findRallyToolConfig(projectConfig);
            if (toolConfig == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ReleaseDataResponse.error("Rally tool configuration not found for project: " + projectId));
            }
            
            // Create and configure the field mapping
            ProjectConfFieldMapping confFieldMapping = createFieldMapping(projectConfig, toolConfig);
            
            // Process release data
            fetchScrumReleaseData.processReleaseInfo(confFieldMapping);
            
            // Return success response
            return ResponseEntity.ok(ReleaseDataResponse.success(
                    "Successfully fetched and stored release data for project " + projectConfig.getProjectName()));
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument while fetching release data", e);
            return ResponseEntity.badRequest()
                    .body(ReleaseDataResponse.error(e.getMessage()));
        } catch (IOException | ParseException e) {
            log.error("Error fetching release data from Rally", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ReleaseDataResponse.error("An error occurred while fetching release data from Rally: " + e.getMessage()));
        }
    }
    
    /**
     * Find project configuration by ID
     * 
     * @param projectId Project ID
     * @return ProjectBasicConfig or null if not found
     */
    private ProjectBasicConfig findProjectConfig(String projectId) {
        return projectBasicConfigRepository.findById(new ObjectId(projectId)).orElse(null);
    }
    
    /**
     * Find Rally tool configuration for a project
     * 
     * @param projectConfig Project configuration
     * @return First matching ProjectToolConfig or null if none found
     */
    private ProjectToolConfig findRallyToolConfig(ProjectBasicConfig projectConfig) {
        List<ProjectToolConfig> toolConfigs = projectToolConfigRepository
                .findByToolNameAndBasicProjectConfigId(RALLY_TOOL_NAME, projectConfig.getId());
        
        return (toolConfigs != null && !toolConfigs.isEmpty()) ? toolConfigs.get(0) : null;
    }
    
    /**
     * Create and configure field mapping for release data processing
     * 
     * @param projectConfig Project configuration
     * @param toolConfig Tool configuration
     * @return Configured ProjectConfFieldMapping
     */
    private ProjectConfFieldMapping createFieldMapping(ProjectBasicConfig projectConfig, ProjectToolConfig toolConfig) {
        ProjectConfFieldMapping confFieldMapping = new ProjectConfFieldMapping();
        confFieldMapping.setProjectBasicConfig(projectConfig);
        confFieldMapping.setProjectToolConfig(toolConfig);
        
        log.info("Initialized ProjectConfFieldMapping with projectBasicConfig ID: {} and projectToolConfig ID: {}", 
                projectConfig.getId(), toolConfig.getId());
        
        return confFieldMapping;
    }
}
