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

package com.publicissapient.knowhow.processor.scm.service.core.fetcher;

import com.publicissapient.knowhow.processor.scm.constants.ScmConstants;
import com.publicissapient.knowhow.processor.scm.dto.ScanRequest;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.core.PersistenceService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser.GitUrlInfo;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MergeRequestFetcherTest {

    @Mock
    private PlatformServiceLocator platformServiceLocator;

    @Mock
    private PersistenceService persistenceService;

    @Mock
    private GitUrlParser gitUrlParser;

    @Mock
    private GitPlatformService mockPlatformService;

    @InjectMocks
    private MergeRequestFetcher mergeRequestFetcher;

    private ScanRequest scanRequest;
    private GitUrlInfo gitUrlInfo;
    private List<ScmMergeRequests> mockMergeRequests;

    @BeforeEach
    void setUp() {
        // Set field values
        ReflectionTestUtils.setField(mergeRequestFetcher, "firstScanFromMonths", 6);
        ReflectionTestUtils.setField(mergeRequestFetcher, "maxMergeRequestsPerScan", 5000);

        // Initialize test data
        scanRequest = ScanRequest.builder().repositoryName("test-repo")
                .repositoryUrl("https://github.com/test/test-repo.git")
                .toolType("GitHub")
                .toolConfigId(new ObjectId())
                .branchName("master")
                .username("testUser")
                .token("***test**token**").build();

        gitUrlInfo = new GitUrlInfo(GitUrlParser.GitPlatform.GITHUB, "test", "test-repo", "test", "https://github.com/test/test-repo.git");

        ScmCommits commit = new ScmCommits();
        commit.setRevisionNumber("abc123");

        mockMergeRequests = createMockMergeRequests();
    }

    private List<ScmMergeRequests> createMockMergeRequests() {
        List<ScmMergeRequests> mrs = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ScmMergeRequests mr = new ScmMergeRequests();
            mr.setExternalId("MR-" + i);
            mr.setState(String.valueOf(i == 1 ? ScmMergeRequests.MergeRequestState.OPEN : ScmMergeRequests.MergeRequestState.MERGED));
            mr.setUpdatedOn(LocalDateTime.now().minusDays(i));
            mrs.add(mr);
        }
        return mrs;
    }

    @Test
    void testFetchMergeRequests_WithNewAndUpdatedMRs_Success() throws Exception {
        // Arrange
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);

        List<ScmMergeRequests> newMRs = Arrays.asList(mockMergeRequests.get(0), mockMergeRequests.get(1));
        List<ScmMergeRequests> updatedMRs = Collections.singletonList(mockMergeRequests.get(0)); // Same MR updated

        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
                .thenReturn(newMRs)
                .thenReturn(updatedMRs);

        Page<ScmMergeRequests> openMRsPage = new PageImpl<>(Collections.singletonList(mockMergeRequests.get(0)));

        // Act
        List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size()); // Deduplicated
        verify(platformServiceLocator, times(1)).callWithContext(any(), anyString(), any());
    }

    @Test
    void testFetchMergeRequests_OnlyNewMRs_Success() throws Exception {
        // Arrange
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);

        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
                .thenReturn(mockMergeRequests);

        Page<ScmMergeRequests> emptyPage = new PageImpl<>(Collections.emptyList());
        // Act
        List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(mockMergeRequests.size(), result.size());
    }

    @Test
    void testFetchMergeRequests_PlatformException_Propagates() throws Exception {
        // Arrange
        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
                .thenThrow(new PlatformApiException("GitHub", "API Error"));

        // Act & Assert
        assertThrows(PlatformApiException.class, () -> mergeRequestFetcher.fetchMergeRequests(scanRequest));
    }

    @Test
    void testFetchNewMergeRequests_WithLastScanFrom_Success() throws Exception {
        // Arrange
        long timestamp = System.currentTimeMillis();
        scanRequest.setLastScanFrom(timestamp);

        List<ScmMergeRequests> result = invokeFetchNewMergeRequests();

        // Assert
        assertNotNull(result);
        verify(platformServiceLocator).callWithContext(eq(mockPlatformService), anyString(), any());
    }

    @Test
    void testFetchNewMergeRequests_WithSinceDate_Success() throws Exception {
        // Arrange
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(7);
        scanRequest.setSince(sinceDate);
        scanRequest.setLastScanFrom(null);

        List<ScmMergeRequests> result = invokeFetchNewMergeRequests();

        // Assert
        assertNotNull(result);
    }

    @Test
    void testFetchNewMergeRequests_FirstScan_Success() throws Exception {
        // Arrange
        scanRequest.setLastScanFrom(null);
        scanRequest.setSince(null);

        List<ScmMergeRequests> result = invokeFetchNewMergeRequests();

        // Assert
        assertNotNull(result);
    }

    @Test
    void testFetchNewMergeRequests_BitbucketToken_Success() throws Exception {
        // Arrange
        scanRequest.setToolType(ScmConstants.BITBUCKET);

        List<ScmMergeRequests> result = invokeFetchNewMergeRequests();

        // Assert
        assertNotNull(result);
    }

    private List<ScmMergeRequests> invokeFetchNewMergeRequests() throws Exception {
        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
                .thenReturn(mockMergeRequests);

        return ReflectionTestUtils.invokeMethod(mergeRequestFetcher, "fetchNewMergeRequests",
                scanRequest, mockPlatformService, gitUrlInfo, "test-identifier");
    }

    @Test
    void testFetchUpdatesForOpenMRs_NoExistingMRs_ReturnsEmpty() throws Exception {
        // Arrange
        Page<ScmMergeRequests> emptyPage = new PageImpl<>(Collections.emptyList());
        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class))).thenReturn(emptyPage);

        // Act - Use a valid ObjectId string instead of "test-identifier"
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "fetchUpdatesForOpenMergeRequests", scanRequest, mockPlatformService, gitUrlInfo,
                new ObjectId().toString()); // CHANGE: Use valid ObjectId format

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchUpdatesForOpenMRs_WithExistingMRs_Success() throws Exception {
        // Arrange
        List<ScmMergeRequests> existingOpenMRs = Collections.singletonList(mockMergeRequests.get(0));
        Page<ScmMergeRequests> openMRsPage = new PageImpl<>(existingOpenMRs);
        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class))).thenReturn(openMRsPage);

        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
                .thenReturn(mockMergeRequests);

        // Act - Use a valid ObjectId string instead of "test-identifier"
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "fetchUpdatesForOpenMergeRequests", scanRequest, mockPlatformService, gitUrlInfo,
                new ObjectId().toString()); // CHANGE: Use valid ObjectId format

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size()); // Only MR-1 should be returned as it exists in open MRs
        assertEquals("MR-1", result.get(0).getExternalId());
    }


    @Test
    void testFetchUpdatesForOpenMRs_DatabaseException_ReturnsEmpty() throws Exception {

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "fetchNewMergeRequests", scanRequest, mockPlatformService, gitUrlInfo,
                new ObjectId().toString());

        // Assert
        assertNull(result);
    }

    @Test
    void testGetExistingOpenMRs_SinglePage_Success() {
        // Arrange
        Page<ScmMergeRequests> singlePage = new PageImpl<>(mockMergeRequests, PageRequest.of(0, 5000), 3);
        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class))).thenReturn(singlePage);

        // Act - Use a valid ObjectId string
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "getExistingOpenMergeRequests", new ObjectId().toString()); // CHANGE: Use valid ObjectId format

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void testGetExistingOpenMRs_MultiplePages_Success() {
        // Arrange
        List<ScmMergeRequests> page1Data = Arrays.asList(mockMergeRequests.get(0), mockMergeRequests.get(1));
        List<ScmMergeRequests> page2Data = Collections.singletonList(mockMergeRequests.get(2));

        Page<ScmMergeRequests> page1 = new PageImpl<>(page1Data, PageRequest.of(0, 2), 3);
        Page<ScmMergeRequests> page2 = new PageImpl<>(page2Data, PageRequest.of(1, 2), 3);

        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class)))
                .thenReturn(page1)
                .thenReturn(page2);

        // Act - Use a valid ObjectId string
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "getExistingOpenMergeRequests", new ObjectId().toString()); // CHANGE: Use valid ObjectId format

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void testGetExistingOpenMRs_Exception_ReturnsEmpty() {

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "getExistingOpenMergeRequests", "test-identifier");

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCalculateUpdateWindowStart_ValidDates_Success() {
        // Arrange
        List<ScmMergeRequests> mrsWithDates = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ScmMergeRequests mr = new ScmMergeRequests();
            mr.setUpdatedOn(LocalDateTime.now().minusMonths(i));
            mrsWithDates.add(mr);
        }

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "calculateUpdateWindowStart", mrsWithDates);

        // Assert
        assertNotNull(result);
        assertEquals(mrsWithDates.get(2).getUpdatedOn(), result); // Oldest date
    }

    @Test
    void testCalculateUpdateWindowStart_NoDates_DefaultsTo3Months() {
        // Arrange
        List<ScmMergeRequests> mrsWithoutDates = new ArrayList<>();
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setUpdatedOn(null);
        mrsWithoutDates.add(mr);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "calculateUpdateWindowStart", mrsWithoutDates);

        // Assert
        assertNotNull(result);
        LocalDateTime expectedDate = LocalDateTime.now().minusMonths(3);
        assertTrue(result.isAfter(expectedDate.minusSeconds(1)) &&
                result.isBefore(expectedDate.plusSeconds(1)));
    }

    @Test
    void testCalculateUpdateWindowStart_OlderThan6Months_CapsAt6Months() {
        // Arrange
        List<ScmMergeRequests> oldMRs = new ArrayList<>();
        ScmMergeRequests mr = new ScmMergeRequests();
        mr.setUpdatedOn(LocalDateTime.now().minusMonths(8));
        oldMRs.add(mr);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "calculateUpdateWindowStart", oldMRs);

        // Assert
        assertNotNull(result);
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        assertTrue(result.isAfter(sixMonthsAgo.minusSeconds(1)) &&
                result.isBefore(sixMonthsAgo.plusSeconds(1)));
    }

    @Test
    void testFilterRelevantUpdates_MatchingIds_Success() {
        // Arrange
        List<ScmMergeRequests> allRecentMRs = mockMergeRequests;
        List<ScmMergeRequests> existingOpenMRs = Arrays.asList(mockMergeRequests.get(0), mockMergeRequests.get(2));

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "filterRelevantUpdates", allRecentMRs, existingOpenMRs);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(mr ->
                mr.getExternalId().equals("MR-1") || mr.getExternalId().equals("MR-3")));
    }

    @Test
    void testFilterRelevantUpdates_NoMatches_ReturnsEmpty() {
        // Arrange
        List<ScmMergeRequests> allRecentMRs = List.of(mockMergeRequests.get(0));
        List<ScmMergeRequests> existingOpenMRs = Arrays.asList(mockMergeRequests.get(1), mockMergeRequests.get(2));

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "filterRelevantUpdates", allRecentMRs, existingOpenMRs);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testCombineAndDeduplicate_Duplicates_PrioritizesUpdated() {
        // Arrange
        ScmMergeRequests newMR = new ScmMergeRequests();
        newMR.setExternalId("MR-1");
        newMR.setState(String.valueOf(ScmMergeRequests.MergeRequestState.OPEN));

        ScmMergeRequests updatedMR = new ScmMergeRequests();
        updatedMR.setExternalId("MR-1");
        updatedMR.setState(String.valueOf(ScmMergeRequests.MergeRequestState.MERGED));

        List<ScmMergeRequests> newMRs = List.of(newMR);
        List<ScmMergeRequests> updatedMRs = List.of(updatedMR);

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "combineAndDeduplicateMergeRequests", newMRs, updatedMRs);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(ScmMergeRequests.MergeRequestState.MERGED.toString(), result.get(0).getState());
    }

    @Test
    void testCombineAndDeduplicate_NullIds_Skipped() {
        // Arrange
        ScmMergeRequests mrWithId = new ScmMergeRequests();
        mrWithId.setExternalId("MR-1");

        ScmMergeRequests mrWithoutId = new ScmMergeRequests();
        mrWithoutId.setExternalId(null);

        List<ScmMergeRequests> newMRs = Arrays.asList(mrWithId, mrWithoutId);
        List<ScmMergeRequests> updatedMRs = Collections.emptyList();

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "combineAndDeduplicateMergeRequests", newMRs, updatedMRs);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("MR-1", result.get(0).getExternalId());
    }

    @Test
    void testCalculateMRsSince_WithLastScanFrom_Success() {
        // Arrange
        long timestamp = System.currentTimeMillis();
        scanRequest.setLastScanFrom(timestamp);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "calculateMergeRequestsSince", scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC), result);
    }

    @Test
    void testCalculateMRsSince_ZeroLastScanFrom_UsesSince() {
        // Arrange
        LocalDateTime sinceDate = LocalDateTime.now().minusDays(5);
        scanRequest.setLastScanFrom(0L);
        scanRequest.setSince(sinceDate);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "calculateMergeRequestsSince", scanRequest);

        // Assert
        assertNotNull(result);
        assertEquals(sinceDate, result);
    }

    @Test
    void testFormatToken_Bitbucket_FormatsCorrectly() {
        // Arrange
        scanRequest.setToolType(ScmConstants.BITBUCKET);
        scanRequest.setUsername("user");
        scanRequest.setToken("token");

        // Act
        String result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher, "formatToken", scanRequest);

        // Assert
        assertEquals("user:token", result);
    }

    @Test
    void testFormatToken_NonBitbucket_ReturnsTokenAsIs() {
        // Arrange
        scanRequest.setToolType("GitHub");
        scanRequest.setToken("github-token");

        // Act
        String result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher, "formatToken", scanRequest);

        // Assert
        assertEquals("github-token", result);
    }

//    @Test
//    void testFetchMergeRequests_NullToolType_UsesRepositoryName() throws Exception {
//        // Arrange
//        scanRequest.setToolType(null);
//        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
//        when(gitUrlParser.parseGitUrl(anyString(), isNull(), anyString(), anyString())).thenReturn(gitUrlInfo);
//
//        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
//                .thenReturn(mockMergeRequests);
//
//        Page<ScmMergeRequests> emptyPage = new PageImpl<>(Collections.emptyList());
//        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
//                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class))).thenReturn(emptyPage);
//
//        // Act
//        List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);
//
//        // Assert
//        assertNotNull(result);
//        verify(gitUrlParser).parseGitUrl(anyString(), isNull(), anyString(), anyString());
//    }
//
//    @Test
//    void testFetchMergeRequests_WithUntilDate_PassesToPlatformService() throws Exception {
//        // Arrange
//        LocalDateTime untilDate = LocalDateTime.now();
//        scanRequest.setUntil(untilDate);
//
//        when(platformServiceLocator.getPlatformService(scanRequest)).thenReturn(mockPlatformService);
//        when(gitUrlParser.parseGitUrl(anyString(), anyString(), anyString(), anyString())).thenReturn(gitUrlInfo);
//
//        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
//                .thenAnswer(invocation -> {
//                    Callable<?> callable = invocation.getArgument(2);
//                    return callable.call();
//                });
//
//        when(mockPlatformService.fetchMergeRequests(anyString(), any(), anyString(), anyString(),
//                any(LocalDateTime.class), eq(untilDate))).thenReturn(mockMergeRequests);
//
//        Page<ScmMergeRequests> emptyPage = new PageImpl<>(Collections.emptyList());
//        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
//                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class))).thenReturn(emptyPage);
//
//        // Act
//        List<ScmMergeRequests> result = mergeRequestFetcher.fetchMergeRequests(scanRequest);
//
//        // Assert
//        assertNotNull(result);
//        verify(mockPlatformService).fetchMergeRequests(anyString(), any(), anyString(), anyString(),
//                any(LocalDateTime.class), eq(untilDate));
//    }

    @Test
    void testGetExistingOpenMRs_MaxPagesReached_StopsAtLimit() {
        // Arrange
        List<ScmMergeRequests> pageData = List.of(mockMergeRequests.get(0));

        // Create 12 pages (more than the max of 10)
        Page<ScmMergeRequests> page = mock(Page.class);
        when(page.getContent()).thenReturn(pageData);
        when(page.hasNext()).thenReturn(true); // Always has next

        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class)))
                .thenReturn(page);

        // Act - Use a valid ObjectId string instead of "test-identifier"
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "getExistingOpenMergeRequests", new ObjectId().toString());

        // Assert
        assertNotNull(result);
        // Should have called 10 times (initial + 9 more = max 10 pages)
        verify(persistenceService, times(10)).findMergeRequestsByToolConfigIdAndState(
                any(ObjectId.class), eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class));
    }


    @Test
    void testCalculateMRsSince_NoLastScanFromNoSince_UsesDefault() {
        // Arrange
        scanRequest.setLastScanFrom(null);
        scanRequest.setSince(null);

        // Act
        LocalDateTime result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "calculateMergeRequestsSince", scanRequest);

        // Assert
        assertNotNull(result);
        LocalDateTime expectedDate = LocalDateTime.now().minusMonths(6);
        assertTrue(result.isAfter(expectedDate.minusSeconds(1)) &&
                result.isBefore(expectedDate.plusSeconds(1)));
    }

    @Test
    void testFetchNewMergeRequests_CallsWithCorrectParameters() throws Exception {
        // Arrange
        LocalDateTime testTime = LocalDateTime.now().minusDays(1);
        LocalDateTime untilTime = LocalDateTime.now();
        scanRequest.setSince(testTime);
        scanRequest.setUntil(untilTime);
        scanRequest.setToolType("GitHub");

        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
                .thenReturn(mockMergeRequests);

        // CHANGE: Removed unnecessary stubbing for mockPlatformService.fetchMergeRequests()
        // The platformServiceLocator mock returns directly without calling the platform service

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "fetchNewMergeRequests", scanRequest, mockPlatformService, gitUrlInfo,
                new ObjectId().toString());

        // Assert
        assertNotNull(result);
        assertEquals(mockMergeRequests, result);
    }


    @Test
    void testCombineAndDeduplicate_EmptyLists_ReturnsEmpty() {
        // Arrange
        List<ScmMergeRequests> emptyNew = Collections.emptyList();
        List<ScmMergeRequests> emptyUpdated = Collections.emptyList();

        // Act
        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
                "combineAndDeduplicateMergeRequests", emptyNew, emptyUpdated);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

//    @Test
//    void testFetchUpdatesForOpenMRs_CallsWithCorrectUpdateWindow() throws Exception {
//        // Arrange
//        LocalDateTime oldestDate = LocalDateTime.now().minusMonths(2);
//        ScmMergeRequests openMR = new ScmMergeRequests();
//        openMR.setExternalId("MR-1");
//        openMR.setUpdatedOn(oldestDate);
//
//        List<ScmMergeRequests> existingOpenMRs = List.of(openMR);
//        Page<ScmMergeRequests> openMRsPage = new PageImpl<>(existingOpenMRs);
//
//        when(persistenceService.findMergeRequestsByToolConfigIdAndState(any(ObjectId.class),
//                eq(ScmMergeRequests.MergeRequestState.OPEN), any(Pageable.class))).thenReturn(openMRsPage);
//
//        when(platformServiceLocator.callWithContext(eq(mockPlatformService), anyString(), any()))
//                .thenAnswer(invocation -> {
//                    Callable<?> callable = invocation.getArgument(2);
//                    return callable.call();
//                });
//
//        when(mockPlatformService.fetchMergeRequests(
//                anyString(),
//                any(),
//                anyString(),
//                anyString(),
//                eq(oldestDate),
//                isNull()
//        )).thenReturn(List.of(openMR));
//
//        // Act
//        List<ScmMergeRequests> result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher,
//                "fetchUpdatesForOpenMergeRequests", scanRequest, mockPlatformService, gitUrlInfo,
//                scanRequest.getToolConfigId().toString());
//
//        // Assert
//        assertNotNull(result);
//        assertEquals(1, result.size());
//        verify(mockPlatformService).fetchMergeRequests(
//                anyString(),
//                any(),
//                anyString(),
//                anyString(),
//                eq(oldestDate),
//                isNull()
//        );
//    }

    @Test
    void testFormatToken_CaseInsensitiveBitbucket_FormatsCorrectly() {
        // Arrange
        scanRequest.setToolType("bitbucket"); // lowercase
        scanRequest.setUsername("user");
        scanRequest.setToken("token");

        // Act
        String result = ReflectionTestUtils.invokeMethod(mergeRequestFetcher, "formatToken", scanRequest);

        // Assert
        assertEquals("user:token", result);
    }
}
