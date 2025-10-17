/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.knowhow.processor.scm.dto;

import lombok.Builder;
import lombok.Data;
import org.bson.types.ObjectId;
import java.time.LocalDateTime;

/**
 * Data class for scan requests.
 * Extracted from GitScannerService to follow Single Responsibility Principle.
 */
@Data
@Builder
public class ScanRequest {
    private String repositoryUrl;
    private String repositoryName;
    private String branchName;
    private String username;
    private String token;
    private String toolType;
    private ObjectId toolConfigId;
    private boolean cloneEnabled;
    private LocalDateTime since;
    private LocalDateTime until;
    private int limit;
    private String commitFetchStrategy;
    private Long lastScanFrom;
}
