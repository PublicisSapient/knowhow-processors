package com.publicissapient.kpidashboard.rally.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.repository.connection.ConnectionRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.model.application.ProjectRelease;
import com.publicissapient.kpidashboard.common.model.application.ProjectVersion;
import com.publicissapient.kpidashboard.common.repository.application.ProjectReleaseRepo;
import com.publicissapient.kpidashboard.common.service.HierarchyLevelService;
import com.publicissapient.kpidashboard.common.service.ProjectHierarchyService;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import com.publicissapient.kpidashboard.rally.model.ProjectConfFieldMapping;
import com.publicissapient.kpidashboard.rally.model.Release;
import com.publicissapient.kpidashboard.rally.util.RallyRestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of FetchScrumReleaseData
 * This class is responsible for fetching release data from Rally API
 * and processing it for the KPI dashboard.
 *
 */
@Service
@Slf4j
public class FetchScrumReleaseDataImpl implements FetchScrumReleaseData {

    public static final String THEME = "Theme";
    public static final String STATE = "State";
    public static final String RELEASE_DATE = "ReleaseDate";
    public static final String RELEASE_START_DATE = "ReleaseStartDate";
    @Autowired
    private ProjectReleaseRepo projectReleaseRepo;

    @Autowired
    private RallyRestClient rallyRestClient;

    @Autowired
    private HierarchyLevelService hierarchyLevelService;

    @Autowired
    private ProjectHierarchyService projectHierarchyService;

    @Autowired
    private ProjectHierarchySyncService projectHierarchySyncService;

    @Autowired
    private ConnectionRepository connectionRepository;

    /**
     * Process release data from Rally
     * This method fetches releases from Rally API and saves them to the database
     *
     * @param projectConfig Project configuration
     * @throws IOException If there's an error in API communication
     * @throws ParseException If there's an error parsing the response
     */
    @Override
    public void processReleaseInfo(ProjectConfFieldMapping projectConfig) throws IOException, ParseException {
        try {
            log.info("Starting processReleaseInfo for project ID: {}, Name: {}",
                    projectConfig.getProjectBasicConfig().getId(),
                    projectConfig.getProjectBasicConfig().getProjectName());

            List<ProjectVersion> projectVersions = getRallyVersions(projectConfig);
            log.info("Fetched {} releases from Rally API for project {}",
                    projectVersions.size(), projectConfig.getProjectBasicConfig().getProjectName());

            if (CollectionUtils.isEmpty(projectVersions)) {
                log.warn("No releases found for project {}, nothing to save",
                        projectConfig.getProjectBasicConfig().getProjectName());
            } else {
                log.info("First few releases: {}",
                        projectVersions.stream().limit(3).map(ProjectVersion::getName).collect(Collectors.joining(", ")));
            }

            // Save project releases
            ProjectRelease projectRelease = saveProjectReleases(projectVersions, projectConfig.getProjectBasicConfig());

            // Save scrum account hierarchy if needed
            if (projectRelease != null) {
                saveScrumAccountHierarchy(projectConfig.getProjectBasicConfig(), projectRelease);
            }

            // Verify the save operation by querying the database
            ProjectRelease savedRelease = projectReleaseRepo.findByConfigId(projectConfig.getProjectBasicConfig().getId());
            if (savedRelease != null) {
                log.info("Verified: Found saved release in database with ID: {}, containing {} versions",
                        savedRelease.getId(),
                        savedRelease.getListProjectVersion() != null ? savedRelease.getListProjectVersion().size() : 0);
            } else {
                log.error("CRITICAL ERROR: Release not found in database after save operation for project {}",
                        projectConfig.getProjectBasicConfig().getProjectName());
            }
        } catch (Exception e) {
            log.error("Error in processReleaseInfo for project {}: {}",
                    projectConfig.getProjectBasicConfig().getProjectName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Save project releases to the database
     *
     * @param projectVersions List of ProjectVersion objects
     * @param projectConfig Project configuration
     * @return The saved ProjectRelease object, or null if the save operation failed
     */
    private ProjectRelease saveProjectReleases(List<ProjectVersion> projectVersions, ProjectBasicConfig projectConfig) {
        if (CollectionUtils.isEmpty(projectVersions)) {
            log.info("No releases to save for project {}", projectConfig.getProjectName());
            return null;
        }

        // Check if projectNodeId is null
        if (projectConfig.getProjectNodeId() == null) {
            log.warn("Project node ID is null for project {}, skipping save operation", projectConfig.getProjectName());
            return null;
        }

        log.info("Saving {} releases for project {} with ID: {}",
                projectVersions.size(), projectConfig.getProjectName(), projectConfig.getId());

        // Find existing project release or create a new one
        ProjectRelease projectRelease = projectReleaseRepo.findByConfigId(projectConfig.getId());

        if (projectRelease != null) {
            log.info("Updating existing ProjectRelease with ID: {} for project {}",
                    projectRelease.getId(), projectConfig.getProjectName());
        } else {
            log.info("Creating new ProjectRelease for project {}", projectConfig.getProjectName());
            projectRelease = new ProjectRelease();
            projectRelease.setProjectName(projectConfig.getProjectName());
            projectRelease.setProjectId(projectConfig.getProjectNodeId());
            projectRelease.setConfigId(projectConfig.getId());
        }

        // Set the project information
        log.debug("Setting project information: Name={}, NodeId={}, ConfigId={}",
                projectConfig.getProjectName(), projectConfig.getProjectNodeId(), projectConfig.getId());

        // Ensure we have the list initialized
        if (projectRelease.getListProjectVersion() == null) {
            log.debug("Initializing empty list of ProjectVersions");
            projectRelease.setListProjectVersion(new ArrayList<>());
        }

        // Clear existing versions and add new ones
        log.debug("Clearing existing versions and adding {} new ones", projectVersions.size());
        projectRelease.getListProjectVersion().clear();
        projectRelease.getListProjectVersion().addAll(projectVersions);

        // Verify the list was updated correctly
        log.debug("Verifying list update: size={}", projectRelease.getListProjectVersion().size());

        // Save the project release
        log.info("Saving ProjectRelease to database...");
        ProjectRelease savedRelease = projectReleaseRepo.save(projectRelease);

        log.info("Successfully saved release with ID: {} containing {} versions for project {}",
                savedRelease.getId(),
                savedRelease.getListProjectVersion() != null ? savedRelease.getListProjectVersion().size() : 0,
                projectConfig.getProjectName());

        // Log the processed versions
        if (!CollectionUtils.isEmpty(savedRelease.getListProjectVersion())) {
            log.debug("Versions saved: {}",
                    savedRelease.getListProjectVersion().stream()
                            .limit(3)
                            .map(ProjectVersion::getName)
                            .collect(Collectors.joining(", ")));
        } else {
            log.warn("Saved release has empty version list despite adding {} versions", projectVersions.size());
        }

        // Double-check that the release was saved correctly
        log.debug("Double-checking save operation by querying database again");
        ProjectRelease verifiedRelease = projectReleaseRepo.findByConfigId(projectConfig.getId());
        if (verifiedRelease != null) {
            log.debug("Verified: Found release in database with ID: {}", verifiedRelease.getId());
            return verifiedRelease;
        } else {
            log.error("CRITICAL ERROR: Release not found in database after save operation");
            return null;
        }
    }

    /**
     * Get all Rally versions/releases
     * This method fetches all releases from Rally API
     *
     * @param projectConfig Project configuration
     * @return List of ProjectVersion objects
     * @throws JsonProcessingException If there's an error processing JSON
     */
    private List<ProjectVersion> getRallyVersions(ProjectConfFieldMapping projectConfig) throws JsonProcessingException {
        // Build the query URL
        String queryUrl = buildRallyQueryUrl(projectConfig);

        // Fetch releases from Rally API
        List<Release> releases = fetchReleasesFromRally(projectConfig, queryUrl);
        if (CollectionUtils.isEmpty(releases)) {
            return Collections.emptyList();
        }

        // Log the response for debugging
        log.info("Rally API returned {} releases", releases.size());

        // Map Rally releases to ProjectVersion objects
        return releases.stream()
                .map(this::processRelease)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Build the Rally API query URL
     *
     * @param projectConfig Project configuration
     * @return Query URL
     */
    private String buildRallyQueryUrl(ProjectConfFieldMapping projectConfig) {
        // Base URL for releases endpoint
        String releasesUrl = String.format("%s/release", rallyRestClient.getBaseUrl());

        // Build the query part
        String queryPart = determineProjectIdentifier(projectConfig);

        // Complete URL with query and pagination
        // Include archived releases and increase page size to ensure we get all releases
        String fullUrl = releasesUrl + "?query=" + queryPart +
                "&pagesize=1000" +
                "&fetch=true" +
                "&includeArchived=true";

        log.info("Built Rally API query URL for project: {}", projectConfig.getProjectBasicConfig().getProjectName());
        log.debug("Fetching releases with URL: {}", fullUrl);

        return fullUrl;
    }

    /**
     * Determine the project identifier to use in the query
     *
     * @param projectConfig Project configuration
     * @return Project identifier query string
     */
    private String determineProjectIdentifier(ProjectConfFieldMapping projectConfig) {
        String queryString;

        // Check if we have a projectKey in the tool config to use instead of ObjectID
        if (projectConfig.getProjectToolConfig() != null &&
                StringUtils.isNotBlank(projectConfig.getProjectToolConfig().getProjectKey())) {
            // Use project name/key instead of ObjectID
            String projectKey = projectConfig.getProjectToolConfig().getProjectKey();
            queryString = String.format("(Project.Name = \"%s\")", projectKey);
            log.debug("Using Project Name: {} for query", projectKey);
        } else {
            // Try with ObjectID
            String projectId = projectConfig.getProjectBasicConfig().getProjectNodeId();
            queryString = String.format("(Project.ObjectID = \"%s\")", projectId);
            log.debug("Using Project ObjectID: {} for query", projectId);
        }
        
        return queryString;
    }
    
    /**
     * Fetch releases from Rally API
     *
     * @param projectConfig Project configuration
     * @param queryUrl Query URL
     * @return List of Release objects or empty list if error
     */
    private List<Release> fetchReleasesFromRally(ProjectConfFieldMapping projectConfig, String queryUrl) {
        List<Release> releases = new ArrayList<>();
        
        try {
            log.info("Fetching releases from Rally API for project: {}", projectConfig.getProjectBasicConfig().getProjectName());
            
            // Create headers with authentication
            HttpHeaders headers = createAuthHeaders(projectConfig);
            
            // Make the API request
            ResponseEntity<String> rawResponse = makeRallyApiRequest(queryUrl, headers);
            
            if (isSuccessfulResponse(rawResponse)) {
                log.info("Successfully fetched releases from Rally API");
                String responseBody = rawResponse.getBody();
                
                // Parse response and extract releases
                releases = parseRallyReleases(responseBody);
                
                log.info("Successfully parsed {} releases from API response", releases.size());
            }
        } catch (Exception e) {
            log.error("Error fetching releases from Rally API: {}", e.getMessage(), e);
        }
        
        return releases;
    }
    
    /**
     * Create authentication headers for Rally API requests
     * 
     * @param projectConfig Project configuration
     * @return HttpHeaders with authentication information
     */
    private HttpHeaders createAuthHeaders(ProjectConfFieldMapping projectConfig) {
        HttpHeaders headers = new HttpHeaders();
        
        if (projectConfig.getProjectToolConfig() != null && 
            projectConfig.getProjectToolConfig().getConnectionId() != null) {
            
            // Get connection details
            Optional<Connection> connectionOpt = connectionRepository.findById(
                projectConfig.getProjectToolConfig().getConnectionId());
                
            if (connectionOpt.isPresent() && connectionOpt.get().getAccessToken() != null) {
                headers.set("zsessionid", connectionOpt.get().getAccessToken());
                headers.set("Accept", "application/json");
                headers.set("Content-Type", "application/json");
            }
        }
        
        return headers;
    }
    
    /**
     * Make a request to the Rally API
     * 
     * @param queryUrl URL to query
     * @param headers HTTP headers
     * @return Response entity with the API response
     */
    private ResponseEntity<String> makeRallyApiRequest(String queryUrl, HttpHeaders headers) {
        RestTemplate customRestTemplate = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        try {
            return customRestTemplate.exchange(queryUrl, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            log.error("Error making request to Rally API: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Check if the API response was successful
     * 
     * @param response Response from the API
     * @return True if successful, false otherwise
     */
    private boolean isSuccessfulResponse(ResponseEntity<String> response) {
        return response != null && 
               response.getStatusCode() == HttpStatus.OK && 
               response.getBody() != null;
    }
    
    /**
     * Parse the Rally API response and extract releases
     * 
     * @param responseBody Response body from the API
     * @return List of Release objects
     */
    private List<Release> parseRallyReleases(String responseBody) {
        List<Release> releases = new ArrayList<>();
        
        try {
            org.json.JSONObject jsonObject = new org.json.JSONObject(responseBody);
            org.json.JSONObject queryResult = jsonObject.getJSONObject("QueryResult");
            org.json.JSONArray resultsArray = queryResult.getJSONArray("Results");
            
            log.info("Found {} releases in API response", resultsArray.length());
            
            for (int i = 0; i < resultsArray.length(); i++) {
                org.json.JSONObject releaseJson = resultsArray.getJSONObject(i);
                Release release = createReleaseFromJson(releaseJson);
                releases.add(release);
            }
        } catch (Exception e) {
            log.error("Error parsing Rally API response: {}", e.getMessage(), e);
        }
        
        return releases;
    }
    
    /**
     * Create a Release object from a JSON object
     * 
     * @param releaseJson JSON object representing a release
     * @return Release object
     */
    private Release createReleaseFromJson(org.json.JSONObject releaseJson) throws JSONException {
        Release release = new Release();
        
        // Set object ID
        if (releaseJson.has("ObjectID")) {
            release.setObjectID(releaseJson.getLong("ObjectID"));
        }
        
        // Set name from _refObjectName or Name
        if (releaseJson.has("_refObjectName")) {
            release.setName(releaseJson.getString("_refObjectName"));
        } else if (releaseJson.has("Name")) {
            release.setName(releaseJson.getString("Name"));
        }
        
        // Set dates
        if (releaseJson.has(RELEASE_START_DATE) && !releaseJson.isNull(RELEASE_START_DATE)) {
            release.setReleaseStartDate(releaseJson.getString(RELEASE_START_DATE));
        }
        
        if (releaseJson.has(RELEASE_DATE) && !releaseJson.isNull(RELEASE_DATE)) {
            release.setReleaseDate(releaseJson.getString(RELEASE_DATE));
        }
        
        // Set state
        if (releaseJson.has(STATE) && !releaseJson.isNull(STATE)) {
            release.setState(releaseJson.getString(STATE));
        }
        
        // Set theme/description
        if (releaseJson.has(THEME) && !releaseJson.isNull(THEME)) {
            release.setTheme(releaseJson.getString(THEME));
        }
        
        // Log for debugging
        log.debug("Parsed release: id={}, name={}, startDate={}, releaseDate={}, state={}",
                release.getObjectID(),
                release.getName(),
                release.getReleaseStartDate(),
                release.getReleaseDate(),
                release.getState());
                
        return release;
    }

    /**
     * Process a Rally release
     *
     * @param release Rally release
     * @return ProjectVersion object
     */
    private ProjectVersion processRelease(Release release) {
        return mapToProjectVersion(release);
    }

    /**
     * Map a Rally release to a ProjectVersion object
     *
     * @param release Rally release
     * @return ProjectVersion object
     */
    private ProjectVersion mapToProjectVersion(Release release) {
        ProjectVersion version = new ProjectVersion();

        // Set basic information
        version.setId(release.getObjectID());
        version.setName(release.getName());
        version.setDescription(release.getTheme());

        // Set dates from API response first
        if (release.getReleaseStartDate() != null) {
            version.setStartDate(parseRallyDate(release.getReleaseStartDate()));
            log.debug("Set start date for release {}: {}", version.getName(), version.getStartDate());
        }

        if (release.getReleaseDate() != null) {
            version.setReleaseDate(parseRallyDate(release.getReleaseDate()));
            log.debug("Set release date for release {}: {}", version.getName(), version.getReleaseDate());
        }

        // Set default dates if no dates are available
        setDefaultDatesIfNeeded(version);

        // Set release status based on state or release date
        if ("Released".equalsIgnoreCase(release.getState())) {
            version.setReleased(true);
            log.debug("Release {} marked as released based on state", version.getName());
        } else {
            // If not explicitly marked as released in Rally, check the release date
            DateTime now = DateTime.now();
            log.debug("Current date for comparison: {}", now);

            if (version.getReleaseDate() != null && version.getReleaseDate().isBefore(now)) {
                // If release date has passed, mark as released
                version.setReleased(true);
                log.info("Marking release {} as released because release date {} is in the past (current date: {})",
                        version.getName(), version.getReleaseDate(), now);
            } else {
                version.setReleased(false);
                if (version.getReleaseDate() != null) {
                    log.debug("Release {} not marked as released because release date {} is in the future (current date: {})",
                            version.getName(), version.getReleaseDate(), now);
                } else {
                    log.debug("Release {} not marked as released because release date is not set", version.getName());
                }
            }
        }

        return version;
    }

    /**
     * Set default dates if no dates are available
     *
     * @param version ProjectVersion to update
     */
    private void setDefaultDatesIfNeeded(ProjectVersion version) {
        if (version.getStartDate() == null || version.getReleaseDate() == null) {
            setDefaultDatesFromReleaseName(version, version.getName());
        }
    }

    /**
     * Parse a Rally date string to a DateTime object
     *
     * @param dateStr Date string
     * @return DateTime object
     */
    private DateTime parseRallyDate(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return null;
        }

        // Normalize date string by replacing Z with +0000
        dateStr = dateStr.replace("Z", "+0000");

        // Try multiple date formats
        String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",    // ISO 8601 with milliseconds
                "yyyy-MM-dd'T'HH:mm:ssZ",        // ISO 8601 without milliseconds
                "yyyy-MM-dd'T'HH:mm:ss.SSS",     // ISO 8601 without timezone
                "yyyy-MM-dd'T'HH:mm:ss",         // ISO 8601 without milliseconds or timezone
                "yyyy-MM-dd"                      // Simple date format
        };

        for (String format : formats) {
            try {
                DateTime parsedDate = DateUtil.stringToDateTime(dateStr, format);
                log.debug("Successfully parsed date with format {}: {}", format, dateStr);
                return parsedDate;
            } catch (Exception e) {
                // Continue to next format
                log.trace("Failed to parse date {} with format {}", dateStr, format);
            }
        }

        log.warn("Failed to parse date after trying multiple formats: {}", dateStr);
        return null;
    }

    /**
     * Set default dates based on release name pattern
     * This method tries to extract quarter and year information from release names
     * like "FY 23 Q4" and set appropriate start and end dates
     *
     * @param version ProjectVersion to set dates for
     * @param releaseName Name of the release
     */
    /**
     * Set default dates based on release name pattern
     * This method tries to extract quarter and year information from release names
     * like "FY 23 Q4" and set appropriate start and end dates
     *
     * @param version ProjectVersion to set dates for
     * @param releaseName Name of the release
     */
    private void setDefaultDatesFromReleaseName(ProjectVersion version, String releaseName) {
        try {
            if (releaseName == null) {
                return;
            }

            // Try to extract FY year and quarter information
            if (releaseName.matches(".*FY\\s*\\d{2}\\s*Q[1-4].*")) {
                // Extract year and quarter
                String yearStr = releaseName.replaceAll(".*FY\\s*(\\d{2})\\s*Q[1-4].*", "$1");
                String quarterStr = releaseName.replaceAll(".*FY\\s*\\d{2}\\s*Q([1-4]).*", "$1");

                int year = 2000 + Integer.parseInt(yearStr); // Assuming 20xx
                int quarter = Integer.parseInt(quarterStr);

                // Set dates based on fiscal quarters
                DateTime startDate = null;
                DateTime endDate = null;

                switch (quarter) {
                    case 1: // Q1: Jan-Mar
                        startDate = new DateTime(year, 1, 1, 0, 0);
                        endDate = new DateTime(year, 3, 31, 23, 59);
                        break;
                    case 2: // Q2: Apr-Jun
                        startDate = new DateTime(year, 4, 1, 0, 0);
                        endDate = new DateTime(year, 6, 30, 23, 59);
                        break;
                    case 3: // Q3: Jul-Sep
                        startDate = new DateTime(year, 7, 1, 0, 0);
                        endDate = new DateTime(year, 9, 30, 23, 59);
                        break;
                    case 4: // Q4: Oct-Dec
                        startDate = new DateTime(year, 10, 1, 0, 0);
                        endDate = new DateTime(year, 12, 31, 23, 59);
                        break;
                    default:
                        // No action needed, will use null dates
                        break;
                }

                if (startDate != null && endDate != null) {
                    version.setStartDate(startDate);
                    version.setReleaseDate(endDate);
                    log.debug("Set dates for {} based on name pattern: startDate={}, releaseDate={}",
                            releaseName, startDate, endDate);
                }
            } else if (releaseName.contains("API") || releaseName.contains("Mock")) {
                // For API or Mock releases, set fixed dates instead of using current date
                DateTime startDate = new DateTime(2023, 1, 1, 0, 0);
                DateTime endDate = new DateTime(2023, 12, 31, 23, 59);
                version.setStartDate(startDate);
                version.setReleaseDate(endDate);
                log.debug("Set fixed dates for API/Mock release {}: startDate={}, releaseDate={}",
                        releaseName, version.getStartDate(), version.getReleaseDate());
            }
        } catch (Exception e) {
            log.error("Error setting default dates from release name: {}", releaseName, e);
        }
    }

    /**
     * Save Scrum account hierarchy
     * This method creates and saves ProjectHierarchy objects for releases
     *
     * @param projectConfig Project basic configuration
     * @param projectRelease Project release with versions
     */
    void saveScrumAccountHierarchy(ProjectBasicConfig projectConfig, ProjectRelease projectRelease) {
        try {
            log.info("Saving releases to account hierarchy for project {}", projectConfig.getProjectName());

            Map<String, ProjectHierarchy> existingHierarchy = projectHierarchyService
                    .getProjectHierarchyMapByConfigIdAndHierarchyLevelId(projectConfig.getId().toString(),
                            CommonConstant.HIERARCHY_LEVEL_ID_RELEASE);

            Set<ProjectHierarchy> setToSave = new HashSet<>();
            List<ProjectHierarchy> hierarchyForRelease = createScrumHierarchyForRelease(projectRelease, projectConfig);
            setToSaveAccountHierarchy(setToSave, hierarchyForRelease, existingHierarchy);
            projectHierarchySyncService.syncReleaseHierarchy(projectConfig.getId(), hierarchyForRelease);

            if (CollectionUtils.isNotEmpty(setToSave)) {
                log.info("Updated Hierarchies {}", setToSave.size());
                projectHierarchyService.saveAll(setToSave);
            }
        } catch (Exception e) {
            log.error("Error saving scrum account hierarchy for project {}: {}",
                    projectConfig.getProjectName(), e.getMessage(), e);
        }
    }

    /**
     * Set account hierarchies to save
     *
     * @param setToSave Set to save
     * @param accountHierarchy Account hierarchy
     * @param existingHierarchy Existing hierarchy
     */
    private void setToSaveAccountHierarchy(Set<ProjectHierarchy> setToSave, List<ProjectHierarchy> accountHierarchy,
                                           Map<String, ProjectHierarchy> existingHierarchy) {
        if (CollectionUtils.isNotEmpty(accountHierarchy)) {
            accountHierarchy.forEach(hierarchy -> {
                if (StringUtils.isNotBlank(hierarchy.getParentId())) {
                    ProjectHierarchy exHiery = existingHierarchy.get(hierarchy.getNodeId());
                    if (null == exHiery) {
                        hierarchy.setCreatedDate(LocalDateTime.now());
                        setToSave.add(hierarchy);
                    } else if (!exHiery.equals(hierarchy)) {
                        exHiery.setBeginDate(hierarchy.getBeginDate());
                        exHiery.setNodeName(hierarchy.getNodeName()); // release name changed
                        exHiery.setEndDate(hierarchy.getEndDate());
                        exHiery.setReleaseState(hierarchy.getReleaseState());
                        setToSave.add(exHiery);
                    }
                }
            });
        }
    }

    /**
     * Create hierarchy for scrum
     *
     * @param projectRelease Project release
     * @param projectBasicConfig Project basic config
     * @return List of project hierarchies
     */
    private List<ProjectHierarchy> createScrumHierarchyForRelease(ProjectRelease projectRelease,
                                                                  ProjectBasicConfig projectBasicConfig) {
        log.info("Create Account Hierarchy for project {}", projectBasicConfig.getProjectName());

        HierarchyLevel hierarchyLevel = hierarchyLevelService.getReleaseHierarchyLevel();

        List<ProjectHierarchy> hierarchyArrayList = new ArrayList<>();
        try {
            if (CollectionUtils.isNotEmpty(projectRelease.getListProjectVersion())) {
                projectRelease.getListProjectVersion().forEach(projectVersion -> {
                    ProjectHierarchy releaseHierarchy = new ProjectHierarchy();
                    releaseHierarchy.setBasicProjectConfigId(projectBasicConfig.getId());
                    releaseHierarchy.setHierarchyLevelId(hierarchyLevel.getHierarchyLevelId());

                    String versionName = projectVersion.getName();
                    String versionId = projectVersion.getId() + CommonConstant.ADDITIONAL_FILTER_VALUE_ID_SEPARATOR +
                            projectBasicConfig.getProjectNodeId();

                    releaseHierarchy.setNodeId(versionId);
                    releaseHierarchy.setNodeName(versionName);
                    releaseHierarchy.setNodeDisplayName(versionName);

                    releaseHierarchy.setBeginDate(ObjectUtils.isNotEmpty(projectVersion.getStartDate())
                            ? projectVersion.getStartDate().toString()
                            : CommonConstant.BLANK);
                    releaseHierarchy.setEndDate(ObjectUtils.isNotEmpty(projectVersion.getReleaseDate())
                            ? projectVersion.getReleaseDate().toString()
                            : CommonConstant.BLANK);

                    releaseHierarchy.setReleaseState((projectVersion.isReleased()) ?
                            CommonConstant.RELEASED : CommonConstant.UNRELEASED);
                    releaseHierarchy.setParentId(projectBasicConfig.getProjectNodeId());

                    hierarchyArrayList.add(releaseHierarchy);
                });
            }
        } catch (Exception e) {
            log.error("Rally Processor Failed to get Account Hierarchy data for project {}: {}",
                    projectBasicConfig.getProjectName(), e, e);
        }
        return hierarchyArrayList;
    }
}
