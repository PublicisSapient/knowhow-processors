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

package com.publicissapient.kpidashboard.rally.util;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.publicissapient.kpidashboard.rally.constant.RallyConstants;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.common.model.ProcessorExecutionTraceLog;
import com.publicissapient.kpidashboard.common.model.application.ProgressStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * @author girpatha
 */

@Service
@Slf4j
public class RallyProcessorUtil {

	private RallyProcessorUtil() {
	}

	private static final String NULL_STR = "null";

	private static final Pattern EXCEPTION_WITH_MESSAGE_PATTERN = Pattern
			.compile("^(\\w+(?:\\.\\w+)*Exception):\\s*(.+)$");

	private static final Pattern EXCEPTION_WITH_STATUS_CODE_PATTERN = Pattern
			.compile("(\\w+(?:\\.\\w+)*Exception)\\{[^}]*statusCode=Optional\\.of\\((\\d+)\\)");

	private static final Pattern ERROR_COLLECTION_PATTERN = Pattern
			.compile("\\[ErrorCollection\\{status=(\\d+), errors=\\{.*\\}, errorMessages=\\[.*\\]\\}\\]");

	private static final Pattern ERROR_WITH_STATUS_CODE_PATTERN = Pattern.compile("Error:\\s*(\\d+)\\s*-\\s*(.*)");

	private static final String UNAUTHORIZED = "Sorry, you are not authorized to access the requested resource.";
	private static final String TO_MANY_REQUEST = "Too many request try after sometime.";
	private static final String OTHER_CLIENT_ERRORS = "An unexpected error has occurred. Please contact the KnowHow Support for assistance.";
	private static final String FORBIDDEN = "Forbidden, check your credentials.";

	/**
	 * This method return UTF-8 decoded string response
	 *
	 * @param jiraResponse
	 *          Object of the Jira Response
	 * @return Decoded String
	 */
	public static String deodeUTF8String(Object jiraResponse) {
		if (jiraResponse == null) {
			return "";
		}
		String responseStr = jiraResponse.toString();
		byte[] responseBytes;
		try {
			CharsetDecoder charsetDecoder = StandardCharsets.UTF_8.newDecoder();
			if (responseStr == null || responseStr.isEmpty() || NULL_STR.equalsIgnoreCase(responseStr)) {
				return StringUtils.EMPTY;
			}
			responseBytes = responseStr.getBytes(StandardCharsets.UTF_8);
			charsetDecoder.decode(ByteBuffer.wrap(responseBytes));
			return new String(responseBytes, StandardCharsets.UTF_8);
		} catch (CharacterCodingException e) {
			log.error("error while decoding String using UTF-8 {}  {}", responseStr, e);
			return StringUtils.EMPTY;
		}
	}

	/**
	 * Formats Input date using ISODateTimeFormatter
	 *
	 * @param date
	 *          date to be formatted
	 * @return formatted Date String
	 */
	public static String getFormattedDate(String date) {
		if (date != null && !date.isEmpty()) {
			try {
				DateTime dateTime = ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(date);
				return ISODateTimeFormat.dateHourMinuteSecondMillis().print(dateTime) + "0000";
			} catch (IllegalArgumentException e) {
				log.error("error while parsing date: {} {}", date, e);
			}
		}

		return "";
	}

	public static String getFormattedDateForSprintDetails(String date) {
		if (date != null && !date.isEmpty()) {
			try {
				DateTime dateTime = ISODateTimeFormat.dateOptionalTimeParser().parseDateTime(date);
				return ISODateTimeFormat.dateHourMinuteSecondMillis().print(dateTime) + "Z";
			} catch (IllegalArgumentException e) {
				log.error("error while parsing date: {} {}", date, e);
			}
		}

		return "";
	}

	/**
	 * Method to fetch progress of chunk based issues processing from context save
	 * into traceLog.
	 *
	 * @param processorExecutionTraceLog
	 *          processorTraceLog
	 * @param stepContext
	 *          stepContext
	 */
	public static ProcessorExecutionTraceLog saveChunkProgressInTrace(
			ProcessorExecutionTraceLog processorExecutionTraceLog, StepContext stepContext) {
		if (stepContext == null) {
			log.error("StepContext is null");
			return null;
		}
		if (processorExecutionTraceLog == null) {
			log.error("ProcessorExecutionTraceLog is not present");
			return null;
		}
		JobExecution jobExecution = stepContext.getStepExecution().getJobExecution();
		int totalIssues = jobExecution.getExecutionContext().getInt(RallyConstants.TOTAL_ISSUES, 0);
		int processedIssues = jobExecution.getExecutionContext().getInt(RallyConstants.PROCESSED_ISSUES, 0);
		int pageStart = jobExecution.getExecutionContext().getInt(RallyConstants.PAGE_START, 0);
		String boardId = jobExecution.getExecutionContext().getString(RallyConstants.BOARD_ID, "");

		List<ProgressStatus> progressStatusList = Optional.ofNullable(processorExecutionTraceLog.getProgressStatusList())
				.orElseGet(ArrayList::new);
		ProgressStatus progressStatus = new ProgressStatus();

		String stepMsg = MessageFormat.format("Process Issues {0} to {1} out of {2}", pageStart, processedIssues,
				totalIssues) + (StringUtils.isNotEmpty(boardId) ? ", Board ID : " + boardId : "");
		progressStatus.setStepName(stepMsg);
		progressStatus.setStatus(BatchStatus.COMPLETED.toString());
		progressStatus.setEndTime(System.currentTimeMillis());
		progressStatusList.add(progressStatus);
		processorExecutionTraceLog.setProgressStatusList(progressStatusList);
		return processorExecutionTraceLog;
	}

	public static String generateLogMessage(Throwable exception) {
		String exceptionMessage = exception.getMessage();

		String logMessage = matchPattern(exceptionMessage, EXCEPTION_WITH_STATUS_CODE_PATTERN, true);
		if (logMessage != null)
			return logMessage;

		logMessage = matchPattern(exceptionMessage, EXCEPTION_WITH_MESSAGE_PATTERN, false);
		if (logMessage != null)
			return logMessage;

		logMessage = matchPattern(exceptionMessage, ERROR_COLLECTION_PATTERN, true);
		if (logMessage != null)
			return logMessage;

		logMessage = matchPattern(exceptionMessage, ERROR_WITH_STATUS_CODE_PATTERN, true);
		if (logMessage != null)
			return logMessage;

		return OTHER_CLIENT_ERRORS;
	}

	private static String matchPattern(String exceptionMessage, Pattern pattern, boolean hasStatusCode) {
		Matcher matcher = pattern.matcher(exceptionMessage);
		if (matcher.find()) {
			if (hasStatusCode) {
				int statusCode = Integer.parseInt(matcher.group(1));
				switch (statusCode) {
					case 401 :
						return UNAUTHORIZED;
					case 429 :
						return TO_MANY_REQUEST;
					case 403 :
						return FORBIDDEN;
					default :
						return OTHER_CLIENT_ERRORS;
				}
			}
			return OTHER_CLIENT_ERRORS;
		}
		return null;
	}
}
