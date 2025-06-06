package com.publicissapient.kpidashboard.rally.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
/**
 * @author girpatha
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RallyReleaseResponse {
    @JsonProperty("QueryResult")
    private QueryResult queryResult;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QueryResult {
        @JsonProperty("_rallyAPIMajor")
        private String rallyAPIMajor;
        @JsonProperty("_rallyAPIMinor")
        private String rallyAPIMinor;
        @JsonProperty("Errors")
        private List<String> errors;
        @JsonProperty("Warnings")
        private List<String> warnings;
        @JsonProperty("TotalResultCount")
        private int totalResultCount;
        @JsonProperty("StartIndex")
        private int startIndex;
        @JsonProperty("PageSize")
        private int pageSize;
        @JsonProperty("Results")
        private List<Release> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Release {
        @JsonProperty("_rallyAPIMajor")
        private String rallyAPIMajor;
        @JsonProperty("_rallyAPIMinor")
        private String rallyAPIMinor;
        @JsonProperty("_ref")
        private String ref;
        @JsonProperty("_refObjectUUID")
        private String refObjectUUID;
        @JsonProperty("_refObjectName")
        private String refObjectName;
        @JsonProperty("_type")
        private String type;
        @JsonProperty("ObjectID")
        private Long id;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Description")
        private String description;
        @JsonProperty("ReleaseStartDate")
        private String releaseStartDate;
        @JsonProperty("ReleaseDate")
        private String releaseDate;
        @JsonProperty("State")
        private String state;
        @JsonProperty("Project")
        private String project;
        @JsonProperty("Released")
        private Boolean released;
    }
}
