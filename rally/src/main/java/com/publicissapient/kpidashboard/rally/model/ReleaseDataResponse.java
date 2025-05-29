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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response model for release data operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseDataResponse {
    
    private String message;
    private String error;
    private boolean success;
    
    /**
     * Create a success response
     * 
     * @param message Success message
     * @return ReleaseDataResponse with success flag set to true
     */
    public static ReleaseDataResponse success(String message) {
        return ReleaseDataResponse.builder()
                .message(message)
                .success(true)
                .build();
    }
    
    /**
     * Create an error response
     * 
     * @param errorMessage Error message
     * @return ReleaseDataResponse with success flag set to false
     */
    public static ReleaseDataResponse error(String errorMessage) {
        return ReleaseDataResponse.builder()
                .error(errorMessage)
                .success(false)
                .build();
    }
}
