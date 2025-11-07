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

import org.thymeleaf.util.StringUtils;

import com.publicissapient.kpidashboard.job.config.validator.ConfigValidator;

import lombok.Data;

@Data
public class SchedulingConfig implements ConfigValidator {
    private Set<String> configValidationErrors = new HashSet<>();

    private String cron;

    @Override
    public void validateConfiguration() {
        if(StringUtils.isEmpty(this.cron)) {
            configValidationErrors.add("The cron expression must not be empty");
        }
    }

    @Override
    public Set<String> getConfigValidationErrors() {
        return Collections.unmodifiableSet(configValidationErrors);
    }
}
