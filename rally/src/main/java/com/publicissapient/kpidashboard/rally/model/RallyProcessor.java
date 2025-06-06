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

import com.publicissapient.kpidashboard.common.constant.ProcessorType;
import com.publicissapient.kpidashboard.common.model.generic.Processor;

import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import lombok.Getter;
import lombok.Setter;
/**
 * @author girpatha
 */
@Getter
@Setter
public class RallyProcessor extends Processor {

	/**
	 * retruns rally processor propotype
	 *
	 * @return RallyProcessor
	 */
	public static RallyProcessor prototype() {
		RallyProcessor protoType = new RallyProcessor();
		protoType.setProcessorName(RallyConstants.RALLY);
		protoType.setOnline(true);
		protoType.setActive(true);
		protoType.setLastSuccess(false);
		protoType.setUpdatedTime(System.currentTimeMillis());
		protoType.setProcessorType(ProcessorType.AGILE_TOOL);
		return protoType;
	}
}
