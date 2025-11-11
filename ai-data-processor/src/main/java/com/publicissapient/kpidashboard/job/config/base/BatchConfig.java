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

package com.publicissapient.kpidashboard.job.config.base;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

@Data
public class BatchConfig implements ConfigValidator {
    private Set<String> configValidationErrors = new HashSet<>();

    private int chunkSize;

    @Override
    public void validateConfiguration() {
        if(chunkSize < 1) {
            configValidationErrors.add(String.format("The chunk size must be a positive integer. Received chunk size %s", this.chunkSize));
        }
    }

    @Override
    public Set<String> getConfigValidationErrors() {
        return Collections.unmodifiableSet(this.configValidationErrors);
    }
}
