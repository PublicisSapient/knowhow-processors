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

package com.publicissapient.kpidashboard.rally.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * @author girpatha
 */
@Data
public class HierarchicalRequirement {
        @JsonProperty("_ref")
        private String ref;
        @JsonProperty("_refObjectName")
        private String refObjectName;
        @JsonProperty("FormattedID")
        private String formattedID;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Owner")
        private Owner owner;
        @JsonProperty("ScheduleState")
        private String scheduleState;
        @JsonProperty("Blocked")
        private boolean blocked;
        @JsonProperty("PlanEstimate")
        private Double planEstimate;
        @JsonProperty("_type")
        private String type;
        @JsonProperty("Iteration")
        private Iteration iteration;
        @JsonProperty("Project")
        private Project project;
        @JsonProperty("CreationDate")
        private String creationDate;
        @JsonProperty("LastUpdateDate")
        private String lastUpdateDate;
        @JsonProperty("ObjectID")
        private String objectID;
        private String currentIteration;
        private List<String> pastIterations; // Track spillover
        @JsonProperty("RevisionHistory")
        private Map<String, String> revisionHistory;
        @JsonProperty("Description")
        private String description;
        private Map<String, Object> additionalProperties = new HashMap<>();
        @JsonProperty("Defects")
        @JsonDeserialize(using = DefectsDeserializer.class)
        private List<HierarchicalRequirement> defects;
        @JsonProperty("Requirement")
        private HierarchicalRequirement requirement;

        /**
         * Custom deserializer for the Defects field to handle various JSON formats
         * This makes the code more robust against API response variations
         */
        public static class DefectsDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<List<HierarchicalRequirement>> {
            @Override
            public List<HierarchicalRequirement> deserialize(com.fasterxml.jackson.core.JsonParser p, 
                    com.fasterxml.jackson.databind.DeserializationContext ctxt) throws java.io.IOException {
                
                try {
                    // Get the current token
                    com.fasterxml.jackson.core.JsonToken token = p.getCurrentToken();
                    
                    // If it's the start of an array, try to parse it
                    if (token == com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
                        List<HierarchicalRequirement> result = new ArrayList<>();
                        // Skip the START_ARRAY token
                        token = p.nextToken();
                        
                        // Read until the end of the array
                        while (token != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                            // Skip non-object tokens
                            if (token == com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                                // Create a new HierarchicalRequirement and populate it manually
                                HierarchicalRequirement defect = new HierarchicalRequirement();
                                 
                                // Parse the object fields
                                while ((token = p.nextToken()) != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                                    if (token == com.fasterxml.jackson.core.JsonToken.FIELD_NAME) {
                                        String fieldName = p.getCurrentName();
                                        token = p.nextToken(); // Move to the value
                                        
                                        // Set the appropriate field based on the field name
                                        if ("_ref".equals(fieldName) && token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                                            defect.setRef(p.getValueAsString());
                                        } else if ("FormattedID".equals(fieldName) && token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
                                            defect.setFormattedID(p.getValueAsString());
                                        }
                                        // Skip other fields - we only need the reference information
                                    }
                                }
                                 
                                result.add(defect);
                            }
                             
                            token = p.nextToken();
                        }
                        
                        return result;
                    } else {
                        // Skip the token if it's not an array
                        p.skipChildren();
                        return new ArrayList<>();
                    }
                } catch (Exception e) {
                    // If there's any error, return an empty list instead of failing
                    p.skipChildren();
                    return new ArrayList<>();
                }
            }
        }
    }