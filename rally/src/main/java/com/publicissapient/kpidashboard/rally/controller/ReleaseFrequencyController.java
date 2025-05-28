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
package com.publicissapient.kpidashboard.rally.controller;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectToolConfigRepository;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.service.FetchScrumReleaseData;

import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for fetching Rally Release data for frequency calculation
 */
@RestController
@RequestMapping("/rally/release")
@Slf4j
public class ReleaseFrequencyController {

    @Autowired
    private FetchScrumReleaseData fetchScrumReleaseData;
    
    @Autowired
    private ProjectBasicConfigRepository projectBasicConfigRepository;
    
    @Autowired
    private ProjectToolConfigRepository projectToolConfigRepository;

    /**
     * Fetch release data from Rally for a project
     * This endpoint fetches release data from Rally and stores it in the project_release collection
     *
     * @param projectId Project ID
     * @return Response indicating success or failure
     */
    @GetMapping("/fetch-data")
    public ResponseEntity<?> fetchReleaseData(
            @RequestParam String projectId) {
        
        try {
            // Find project configuration by ID
            ProjectBasicConfig projectConfig = projectBasicConfigRepository.findById(new ObjectId(projectId)).orElse(null);
            if (projectConfig == null) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Project not found with ID: " + projectId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            
            // Find the Rally tool config for this project
            List<ProjectToolConfig> toolConfigs = projectToolConfigRepository.findByToolNameAndBasicProjectConfigId(
                    "Rally", projectConfig.getId());
        
            if (toolConfigs == null || toolConfigs.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "Rally tool configuration not found for project: " + projectId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }
            
            // Create project configuration field mapping
            ProjectConfFieldMapping confFieldMapping = new ProjectConfFieldMapping();
            confFieldMapping.setProjectBasicConfig(projectConfig);
            confFieldMapping.setProjectToolConfig(toolConfigs.get(0));
            
            log.info("Initialized ProjectConfFieldMapping with projectBasicConfig ID: {} and projectToolConfig ID: {}", 
                    projectConfig.getId(), toolConfigs.get(0).getId());
            
            // Process release data from Rally
            fetchScrumReleaseData.processReleaseInfo(confFieldMapping);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "Successfully fetched and stored release data for project " + 
                    projectConfig.getProjectName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (IOException | ParseException e) {
            log.error("Error fetching release data from Rally", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "An error occurred while fetching release data from Rally: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * Validate month and year values
     *
     * @param month Month (1-12)
     * @param year Year
     * @throws IllegalArgumentException if values are invalid
     */
    private void validateMonthAndYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100");
        }
    }
}
