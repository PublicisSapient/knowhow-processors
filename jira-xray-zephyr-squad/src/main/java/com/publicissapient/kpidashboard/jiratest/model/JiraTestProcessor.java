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

package com.publicissapient.kpidashboard.jiratest.model;

import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.constant.ProcessorType;
import com.publicissapient.kpidashboard.common.model.generic.Processor;

/**
 * @author HirenKumar Babariya The type Jira Test processor.
 */
public class JiraTestProcessor extends Processor {

	/**
	 * Prototype Jira Test processor.
	 *
	 * @return the Jira Test processor
	 */
	public static JiraTestProcessor prototype() {
		JiraTestProcessor protoType = new JiraTestProcessor();
		protoType.setProcessorName(ProcessorConstants.JIRA_TEST);
		protoType.setOnline(true);
		protoType.setActive(true);
		protoType.setLastSuccess(false);
		protoType.setProcessorType(ProcessorType.TESTING_TOOLS);
		protoType.setUpdatedTime(System.currentTimeMillis());

		return protoType;
	}
}
