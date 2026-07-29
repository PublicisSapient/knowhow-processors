package com.publicissapient.kpidashboard.job.storyhygienecalculation.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.BoardMetadata;
import com.publicissapient.kpidashboard.common.model.jira.HygieneKpiResponseDTO;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.Metadata;
import com.publicissapient.kpidashboard.common.model.jira.MetadataValue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.StoryHygieneSprintResult;
import com.publicissapient.kpidashboard.common.repository.jira.BoardMetadataRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.repository.jira.StoryHygieneSprintResultRepository;
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;
import com.publicissapient.kpidashboard.job.constant.JobConstants;
import com.publicissapient.kpidashboard.job.storyhygienecalculation.config.StoryHygieneCalculationJobConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Computes Story Hygiene results for all eligible sprints of one project.
 *
 * <p>Cache-hit sprints (hash matches) are skipped — the caller only receives results that need
 * upserting (new or stale). The LLM is called synchronously; the Spring Batch async wrapper in the
 * strategy handles per-project parallelism.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryHygieneCalculationService {

	private static final String SPRINT_STATE_CLOSED = "closed";

	private final AiGatewayClient aiGatewayClient;
	private final SprintRepository sprintRepository;
	private final JiraIssueRepository jiraIssueRepository;
	private final BoardMetadataRepository boardMetadataRepository;
	private final StoryHygieneSprintResultRepository hygieneResultRepository;
	private final ObjectMapper objectMapper;
	private final StoryHygieneCalculationJobConfig jobConfig;

	/**
	 * Evaluates a project's eligible sprints and returns {@link StoryHygieneSprintResult} objects
	 * that need to be upserted (cache misses or stale hash). Fresh cache-hit sprints are excluded.
	 *
	 * @param fieldMapping the field mapping for the project; must have the hygiene rule-set set
	 * @return list of results to persist (may be empty if all sprints are already up to date)
	 */
	public List<StoryHygieneSprintResult> computeForProject(FieldMapping fieldMapping) {
		String basicProjectConfigId = fieldMapping.getBasicProjectConfigId().toString();
		List<CycleTimeGroup> cycleTimeGroups = fieldMapping.getJiraFieldsSelectionKPI311();

		if (CollectionUtils.isEmpty(cycleTimeGroups)) {
			log.debug(
					"{} No hygiene rule-set configured for project {} — skipping",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					basicProjectConfigId);
			return List.of();
		}

		String ruleSetHash = HygienePromptBuilder.computeRuleSetHash(cycleTimeGroups, objectMapper);

		// Fetch N most-recent closed sprints
		int sprintCount = jobConfig.getCalculationConfig().getSprintCount();
		List<SprintDetails> closedSprints =
				sprintRepository.findByBasicProjectConfigIdAndStateIgnoreCaseOrderByStartDateASC(
						new ObjectId(basicProjectConfigId), SPRINT_STATE_CLOSED);
		if (closedSprints.isEmpty()) {
			log.debug(
					"{} No closed sprints for project {} — skipping",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					basicProjectConfigId);
			return List.of();
		}
		List<SprintDetails> recentSprints =
				closedSprints.stream().skip(Math.max(0, closedSprints.size() - sprintCount)).toList();

		// Bulk-fetch existing results to identify cache hits
		List<String> sprintIds = recentSprints.stream().map(SprintDetails::getSprintID).toList();
		Map<String, StoryHygieneSprintResult> cachedBySprintId =
				hygieneResultRepository
						.findByBasicProjectConfigIdAndSprintIdIn(basicProjectConfigId, sprintIds)
						.stream()
						.collect(Collectors.toMap(StoryHygieneSprintResult::getSprintId, r -> r));

		// Build label→fieldName map from BoardMetadata
		Map<String, String> labelToFieldName = buildLabelToFieldNameMap(basicProjectConfigId);

		// Build prompts map (ruleName → criteria)
		Map<String, String> prompts =
				cycleTimeGroups.stream()
						.filter(ctg -> ctg != null && ctg.getLabel() != null && ctg.getPrompt() != null)
						.collect(
								Collectors.toMap(
										CycleTimeGroup::getLabel,
										CycleTimeGroup::getPrompt,
										(a, b) -> a,
										LinkedHashMap::new));

		// Collect fields we need to fetch from Jira
		List<String> anchorFields = jobConfig.getCalculationConfig().getAnchorFields();
		Set<String> jiraFields = new HashSet<>(anchorFields);
		cycleTimeGroups.stream()
				.filter(Objects::nonNull)
				.map(CycleTimeGroup::getLabel)
				.filter(Objects::nonNull)
				.map(labelToFieldName::get)
				.filter(StringUtils::isNotEmpty)
				.forEach(jiraFields::add);
		jiraFields.addAll(List.of("sprintID", "priority", "changeDate"));

		// Fetch Jira issues for all sprints in one DB call
		List<JiraIssue> allIssues =
				jiraIssueRepository.findBySprintIDInAndBasicProjectConfigIdWithFields(
						new HashSet<>(sprintIds), basicProjectConfigId, jiraFields);
		Map<String, List<JiraIssue>> issuesBySprint =
				allIssues.stream()
						.filter(ji -> ji.getSprintID() != null)
						.collect(Collectors.groupingBy(JiraIssue::getSprintID));

		int issueCountCap = jobConfig.getCalculationConfig().getIssueCountPerSprint();
		List<StoryHygieneSprintResult> toUpsert = new ArrayList<>();

		for (SprintDetails sprint : recentSprints) {
			String sprintId = sprint.getSprintID();
			String sprintName = sprint.getSprintName();
			List<JiraIssue> sprintIssues = issuesBySprint.getOrDefault(sprintId, List.of());

			if (sprintIssues.isEmpty()) {
				continue;
			}

			// Cache hit — skip this sprint
			StoryHygieneSprintResult cached = cachedBySprintId.get(sprintId);
			if (cached != null && ruleSetHash.equals(cached.getRuleSetHash())) {
				log.debug(
						"{} Cache hit for project {} sprint '{}' — skipping LLM",
						JobConstants.LOG_PREFIX_STORY_HYGIENE,
						basicProjectConfigId,
						sprintName);
				continue;
			}

			// Determine sample
			int totalIssueCount = sprintIssues.size();
			List<JiraIssue> sample =
					totalIssueCount <= issueCountCap
							? sprintIssues
							: sprintIssues.stream()
									.sorted(
											Comparator.comparingInt(
															(JiraIssue ji) -> HygienePromptBuilder.priorityRank(ji.getPriority()))
													.thenComparing(
															ji -> ji.getChangeDate() != null ? ji.getChangeDate() : "",
															Comparator.reverseOrder()))
									.limit(issueCountCap)
									.toList();

			// Build issue nodes
			List<ObjectNode> issueNodes =
					sample.stream()
							.map(
									ji ->
											HygienePromptBuilder.buildIssueNode(
													ji, anchorFields, cycleTimeGroups, labelToFieldName, objectMapper))
							.toList();

			String prompt =
					HygienePromptBuilder.buildPrompt(prompts, issueNodes, labelToFieldName, objectMapper);
			if (prompt == null) {
				log.warn(
						"{} Failed to build prompt for project {} sprint '{}' — skipping",
						JobConstants.LOG_PREFIX_STORY_HYGIENE,
						basicProjectConfigId,
						sprintName);
				continue;
			}

			// Call LLM — null means the gateway failed; skip persist so the sprint stays a cache miss
			List<HygieneKpiResponseDTO> verdicts = callLlm(prompt, basicProjectConfigId, sprintName);
			if (verdicts == null) {
				continue;
			}

			// Build result — upsert in place if doc already existed (stale hash)
			StoryHygieneSprintResult result =
					cached != null
							? cached
							: StoryHygieneSprintResult.builder()
									.basicProjectConfigId(basicProjectConfigId)
									.sprintId(sprintId)
									.build();
			result.setSprintName(sprintName);
			result.setRuleSetHash(ruleSetHash);
			result.setSampledIssueCount(sample.size());
			result.setTotalIssueCount(totalIssueCount);
			result.setIssueVerdicts(verdicts);
			result.setComputedAt(Instant.now());

			toUpsert.add(result);
		}

		return toUpsert;
	}

	/**
	 * Calls the LLM and returns parsed verdicts, or {@code null} if the gateway failed or returned
	 * blank content. A {@code null} return means the sprint must NOT be persisted — it stays a cache
	 * miss so the next load retries the LLM.
	 */
	private List<HygieneKpiResponseDTO> callLlm(
			String prompt, String basicProjectConfigId, String sprintName) {
		try {
			ChatGenerationResponseDTO response =
					aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
			String content = response == null ? null : response.content();
			if (StringUtils.isBlank(content)) {
				log.warn(
						"{} AI Gateway returned blank content for project {} sprint '{}' — sprint will remain a cache miss",
						JobConstants.LOG_PREFIX_STORY_HYGIENE,
						basicProjectConfigId,
						sprintName);
				return null;
			}
			return objectMapper.readValue(content, new TypeReference<List<HygieneKpiResponseDTO>>() {});
		} catch (Exception ex) {
			log.error(
					"{} LLM call failed for project {} sprint '{}': {} — sprint will remain a cache miss",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					basicProjectConfigId,
					sprintName,
					ex.getMessage(),
					ex);
			return null;
		}
	}

	private Map<String, String> buildLabelToFieldNameMap(String basicProjectConfigId) {
		try {
			BoardMetadata boardMetadata =
					boardMetadataRepository.findByProjectBasicConfigId(new ObjectId(basicProjectConfigId));
			if (boardMetadata == null || CollectionUtils.isEmpty(boardMetadata.getMetadata())) {
				return Map.of();
			}
			return boardMetadata.getMetadata().stream()
					.filter(Objects::nonNull)
					.filter(metadata -> "fields".equalsIgnoreCase(metadata.getType()))
					.map(Metadata::getValue)
					.filter(Objects::nonNull)
					.flatMap(List::stream)
					.filter(mv -> mv != null && mv.getKey() != null)
					.collect(
							Collectors.toMap(
									MetadataValue::getKey,
									MetadataValue::getData,
									(first, second) -> first,
									LinkedHashMap::new));
		} catch (Exception ex) {
			log.warn(
					"{} Could not load BoardMetadata for project {}: {}",
					JobConstants.LOG_PREFIX_STORY_HYGIENE,
					basicProjectConfigId,
					ex.getMessage());
			return Map.of();
		}
	}
}
