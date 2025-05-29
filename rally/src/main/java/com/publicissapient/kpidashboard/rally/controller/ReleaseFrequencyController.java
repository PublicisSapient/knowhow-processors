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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.rally.model.ReleaseDataResponse;
import com.publicissapient.kpidashboard.rally.service.ReleaseDataService;

import lombok.extern.slf4j.Slf4j;

/**
 * REST API controller for fetching Rally Release data for frequency calculation
 */
@RestController
@RequestMapping("/rally/release")
@Slf4j
public class ReleaseFrequencyController {

    private final ReleaseDataService releaseDataService;
    
    /**
     * Constructor for dependency injection
     * 
     * @param releaseDataService Service for handling release data operations
     */
    @Autowired
    public ReleaseFrequencyController(ReleaseDataService releaseDataService) {
        this.releaseDataService = releaseDataService;
    }

    /**
     * Fetch release data from Rally for a project
     * This endpoint fetches release data from Rally and stores it in the project_release collection
     *
     * @param projectId Project ID
     * @return Response containing success message or error details
     */
    @GetMapping("/fetch-data")
    public ResponseEntity<ReleaseDataResponse> fetchReleaseData(@RequestParam String projectId) {
        log.info("Received request to fetch release data for project ID: {}", projectId);
        return releaseDataService.fetchAndProcessReleaseData(projectId);
    }
}
