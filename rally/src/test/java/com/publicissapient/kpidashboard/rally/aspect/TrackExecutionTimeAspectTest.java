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

package com.publicissapient.kpidashboard.rally.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TrackExecutionTimeAspectTest {

    @InjectMocks
    private PerformanceLoggingAspect performanceLoggingAspect;

    private ProceedingJoinPoint proceedingJoinPoint;
    private MethodSignature methodSignature;

    @BeforeEach
    public void setup() {
        proceedingJoinPoint = mock(ProceedingJoinPoint.class);
        methodSignature = mock(MethodSignature.class);
    }

    @Test
    public void testExecutionTime() throws Throwable {
        // Setup
        String className = "TestClass";
        String methodName = "testMethod";
        String returnValue = "Test Result";

        // Mock method signature
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(TestClass.class);
        when(methodSignature.getName()).thenReturn(methodName);
        when(proceedingJoinPoint.proceed()).thenReturn(returnValue);

        // Execute
        Object result = performanceLoggingAspect.executionTime(proceedingJoinPoint);

        // Verify
        assertEquals(returnValue, result);
    }

    @Test
    public void testExecutionTimeWithException() throws Throwable {
        // Setup
        String className = "TestClass";
        String methodName = "testMethod";
        RuntimeException exception = new RuntimeException("Test Exception");

        // Mock method signature
        when(proceedingJoinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getDeclaringType()).thenReturn(TestClass.class);
        when(methodSignature.getName()).thenReturn(methodName);
        when(proceedingJoinPoint.proceed()).thenThrow(exception);

        try {
            // Execute
            performanceLoggingAspect.executionTime(proceedingJoinPoint);
        } catch (RuntimeException e) {
            // Verify
            assertEquals(exception, e);
        }
    }

    private static class TestClass {
    }
}
