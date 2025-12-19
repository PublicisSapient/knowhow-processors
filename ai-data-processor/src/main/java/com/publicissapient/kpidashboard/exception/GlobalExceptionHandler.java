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

package com.publicissapient.kpidashboard.exception;

import static com.publicissapient.kpidashboard.exception.utils.ExceptionUtils.API_EXCEPTION_LOG_MESSAGE;
import static com.publicissapient.kpidashboard.exception.utils.ExceptionUtils.GENERIC_ERROR_MESSAGE;

import java.util.stream.Collectors;

import javax.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.publicissapient.kpidashboard.exception.dto.ErrorResponseRecord;
import com.publicissapient.kpidashboard.exception.utils.ExceptionUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseRecord> handleBadRequestException(BadRequestException exception) {
        return logAndBuildResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseRecord> handleResourceNotFoundException(ResourceNotFoundException exception) {
        return logAndBuildResponse(exception, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseRecord> handleBadCredentialsException(BadCredentialsException exception) {
        return logAndBuildResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorResponseRecord> handleInternalServerErrorException(InternalServerErrorException exception) {
        return logAndBuildResponse(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseRecord> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException methodArgumentNotValidException
    ) {
        return logAndBuildResponse(
                methodArgumentNotValidException,
                ExceptionUtils.VALIDATION_ERROR_MESSAGE,
                getMethodArgumentNotValidExceptionDetails(methodArgumentNotValidException),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(JobNotEnabledException.class)
    public ResponseEntity<ErrorResponseRecord> handleJobNotEnabledException(
            JobNotEnabledException jobNotEnabledException
    ) {
        return logAndBuildResponse(jobNotEnabledException, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ConcurrentJobExecutionException.class)
    public ResponseEntity<ErrorResponseRecord> handleJobIsAlreadyRunningException(
            ConcurrentJobExecutionException concurrentJobExecutionException
    ) {
        return logAndBuildResponse(concurrentJobExecutionException, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseRecord> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        return logAndBuildResponse(
                exception,
                ExceptionUtils.VALIDATION_ERROR_MESSAGE,
                getConstraintViolationExceptionDetails(exception),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseRecord> handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        return logAndBuildResponse(exception, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseRecord> handleGeneralException(Exception exception) {
        return logAndBuildResponse(exception, exception.getMessage(), GENERIC_ERROR_MESSAGE, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static String getMethodArgumentNotValidExceptionDetails(
            MethodArgumentNotValidException methodArgumentNotValidException
    ) {
        return methodArgumentNotValidException.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
    }

    private static String getConstraintViolationExceptionDetails(
            ConstraintViolationException exception
    ) {
        StringBuilder message = new StringBuilder();
        exception.getConstraintViolations().stream().map(constraintViolation -> String.format(
                "Invalid value %s - %s. ",
                constraintViolation.getInvalidValue(),
                constraintViolation.getMessage()
        )).forEach(message::append);
        return message.toString();
    }

    private static ResponseEntity<ErrorResponseRecord> logAndBuildResponse(
            Throwable exception, HttpStatus httpStatus
    ) {
        log.error(API_EXCEPTION_LOG_MESSAGE, exception.getMessage());
        return new ResponseEntity<>(new ErrorResponseRecord(exception.getMessage()), httpStatus);
    }

    private static ResponseEntity<ErrorResponseRecord> logAndBuildResponse(
            Throwable exception, String logMessage, String message, HttpStatus httpStatus
    ) {
        log.error(API_EXCEPTION_LOG_MESSAGE, logMessage, exception);
        return new ResponseEntity<>(new ErrorResponseRecord(message), httpStatus);
    }
}
