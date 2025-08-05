package com.publicissapient.knowhow.processor.scm.client.bitbucket;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.processor.scm.client.wrapper.BitbucketParser;
import com.publicissapient.knowhow.processor.scm.client.wrapper.impl.CloudBitBucketParser;
import com.publicissapient.knowhow.processor.scm.client.wrapper.impl.ServerBitbucketParser;
import com.publicissapient.knowhow.processor.scm.exception.PlatformApiException;
import com.publicissapient.knowhow.processor.scm.service.ratelimit.RateLimitService;
import com.publicissapient.knowhow.processor.scm.util.GitUrlParser;

import reactor.core.publisher.Mono;

/**
 * Bitbucket API client for interacting with Bitbucket repositories.
 * Supports both Bitbucket Cloud (bitbucket.org) and Bitbucket Server (on-premise).
 */
@Component
public class BitbucketClient {

    private static final Logger logger = LoggerFactory.getLogger(BitbucketClient.class);

    @Value("${git-scanner.platforms.bitbucket.api-url:https://api.bitbucket.org/2.0}")
    private String defaultBitbucketApiUrl;

    private final GitUrlParser gitUrlParser;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final WebClient.Builder webClientBuilder;

    public BitbucketClient(GitUrlParser gitUrlParser, RateLimitService rateLimitService,
                           ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
        this.gitUrlParser = gitUrlParser;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.webClientBuilder = webClientBuilder;
    }

    /**
     * Creates a WebClient for Bitbucket API calls.
     */
    public WebClient getBitbucketClient(String username, String appPassword, String apiBaseUrl) {
        String credentials = Base64.getEncoder().encodeToString((username + ":" + appPassword).getBytes());

        return webClientBuilder
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Creates a WebClient from repository URL.
     */
    public WebClient getBitbucketClientFromRepoUrl(String username, String appPassword, String repositoryUrl) {
        String apiBaseUrl = getApiUrlFromRepoUrl(repositoryUrl);
        return getBitbucketClient(username, appPassword, apiBaseUrl);
    }

    /**
     * Fetches commits from a Bitbucket repository.
     */
    public List<BitbucketCommit> fetchCommits(String owner, String repository, String branchName,
                                              String username, String appPassword, LocalDateTime since,
                                              LocalDateTime until, String repositoryUrl) throws PlatformApiException {
        try {
            rateLimitService.checkRateLimit("Bitbucket", username + ":" + appPassword, repository,
                    getApiUrlFromRepoUrl(repositoryUrl));

            WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
            List<BitbucketCommit> allCommits = new ArrayList<>();
            StringBuilder urlBuilder;
            boolean hasParams = false;
            boolean isBitbucketCloud = repositoryUrl.contains("bitbucket.org");

            if(isBitbucketCloud) {
                urlBuilder = new StringBuilder(String.format("/repositories/%s/%s/commits", owner, repository));
                if (branchName != null && !branchName.trim().isEmpty()) {
                    urlBuilder.append("?include=").append(branchName);
                    hasParams = true;
                }
                if (since != null) {
                    urlBuilder.append(hasParams ? "&" : "?");
                    // Use a raw date format that works with Bitbucket API
                    urlBuilder.append("q=").append(URLEncoder.encode("date >= " + since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), StandardCharsets.UTF_8));
                }
            }
            else {
                urlBuilder = new StringBuilder(String.format("/projects/%s/repos/%s/commits", owner, repository));
                if (branchName != null && !branchName.trim().isEmpty()) {
                    urlBuilder.append("?until=refs/heads/").append(branchName);
                    urlBuilder.append("&limit=100");
                    hasParams = true;
                }
            }

            String nextUrl = urlBuilder.toString();
            int fetchedCount = 0;

            while (nextUrl != null) {
                logger.debug("Fetching commits from: {}", nextUrl);
                String decodedUrl = URLDecoder.decode(nextUrl, StandardCharsets.UTF_8.name());
                String response = client.get()
                        .uri(decodedUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                BitbucketCommitsResponse commitsResponse = parseCommitsResponse(response, isBitbucketCloud);

                if (commitsResponse.getValues() != null) {
                    for (BitbucketCommit commit : commitsResponse.getValues()) {
                        // Filter by date if specified
                        if (isCommitInDateRange(commit, since, until)) {
                            allCommits.add(commit);
                            fetchedCount++;
                        }
                    }
                }

                nextUrl = commitsResponse.getNext();
                if (nextUrl != null && nextUrl.startsWith("http")) {
                    // Extract path from full URL for Bitbucket Cloud
                    if (isBitbucketCloud) {
                        nextUrl = nextUrl.substring(nextUrl.indexOf("/repositories"));
                    }
                } else if (nextUrl != null && !isBitbucketCloud) {
                    nextUrl = commitsResponse.getNext();
                    if (nextUrl != null) {
                        if (isBitbucketCloud && nextUrl.startsWith("http")) {
                            // Extract path from full URL for Bitbucket Cloud
                            nextUrl = nextUrl.substring(nextUrl.indexOf("/repositories"));
                        } else if (!isBitbucketCloud && nextUrl.startsWith("nextPageStart=")) {
                            // For Bitbucket Server, construct the next URL with pagination
                            String pageStart = nextUrl.substring("nextPageStart=".length());
                            String baseUrl = urlBuilder.toString();
                            if (baseUrl.contains("?")) {
                                nextUrl = baseUrl + "&start=" + pageStart;
                            } else {
                                nextUrl = baseUrl + "?start=" + pageStart;
                            }
                        }
                    }
                }
            }

            logger.info("Fetched {} commits from Bitbucket repository {}/{}", allCommits.size(), owner, repository);
            return allCommits;

        } catch (WebClientResponseException e) {
            logger.error("Error fetching commits from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch commits from Bitbucket: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Bitbucket response: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to parse Bitbucket response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching commits from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Parses commits response from both Bitbucket Cloud and Server APIs
     */
    private BitbucketCommitsResponse parseCommitsResponse(String response, boolean isBitbucketCloud) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(response);
        BitbucketCommitsResponse commitsResponse = new BitbucketCommitsResponse();
        List<BitbucketCommit> commits = new ArrayList<>();

        JsonNode valuesNode = rootNode.get("values");
        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode commitNode : valuesNode) {
                BitbucketCommit commit = parseCommitNode(commitNode, isBitbucketCloud);
                commits.add(commit);
            }
        }

        commitsResponse.setValues(commits);

        // Handle pagination
        if (isBitbucketCloud) {
            JsonNode nextNode = rootNode.get("next");
            if (nextNode != null && !nextNode.isNull()) {
                commitsResponse.setNext(nextNode.asText());
            }
        } else {
            // Bitbucket Server pagination
            JsonNode isLastPageNode = rootNode.get("isLastPage");
            JsonNode nextPageStartNode = rootNode.get("nextPageStart");
            if (isLastPageNode != null && !isLastPageNode.asBoolean() && nextPageStartNode != null) {
                // For server, we'll set a marker that the calling method can use
                commitsResponse.setNext("nextPageStart=" + nextPageStartNode.asInt());
            }
        }

        return commitsResponse;
    }

    /**
     * Parses individual commit node from both Bitbucket Cloud and Server APIs
     */
    private BitbucketCommit parseCommitNode(JsonNode commitNode, boolean isBitbucketCloud) {
        BitbucketCommit commit = new BitbucketCommit();

        if (isBitbucketCloud) {
            // Bitbucket Cloud format
            JsonNode hashNode = commitNode.get("hash");
            if (hashNode != null) {
                commit.setHash(hashNode.asText());
            }

            JsonNode dateNode = commitNode.get("date");
            if (dateNode != null) {
                commit.setDate(dateNode.asText());
            }

            JsonNode messageNode = commitNode.get("message");
            if (messageNode != null) {
                commit.setMessage(messageNode.asText());
            }

            // Parse author for Bitbucket Cloud
            JsonNode authorNode = commitNode.get("author");
            if (authorNode != null) {
                BitbucketUser author = new BitbucketUser();

                JsonNode typeNode = authorNode.get("type");
                if (typeNode != null) {
                    author.setType(typeNode.asText());
                }

                JsonNode userNode = authorNode.get("user");
                if (userNode != null) {
                    BbUser user = new BbUser();

                    JsonNode displayNameNode = userNode.get("display_name");
                    if (displayNameNode != null) {
                        user.setDisplayName(displayNameNode.asText());
                    }

                    JsonNode usernameNode = userNode.get("nickname");
                    if (usernameNode != null) {
                        user.setUsername(usernameNode.asText());
                    }

                    JsonNode uuidNode = userNode.get("uuid");
                    if (uuidNode != null) {
                        user.setUuid(uuidNode.asText());
                    }

                    JsonNode accountIdNode = userNode.get("account_id");
                    if (accountIdNode != null) {
                        user.setAccountId(accountIdNode.asText());
                    }

                    author.setUser(user);
                }

                // Also set direct properties for compatibility
                JsonNode rawNode = authorNode.get("raw");
                if (rawNode != null) {
                    String rawAuthor = rawNode.asText();
                    // Extract email from "name <email>" format
                    if (rawAuthor.contains("<") && rawAuthor.contains(">")) {
                        String email = rawAuthor.substring(rawAuthor.indexOf("<") + 1, rawAuthor.indexOf(">"));
                        author.setEmailAddress(email);
                        String name = rawAuthor.substring(0, rawAuthor.indexOf("<")).trim();
                        author.setName(name);
                    }
                }

                commit.setAuthor(author);
            }

        } else {
            // Bitbucket Server format
            JsonNode idNode = commitNode.get("id");
            if (idNode != null) {
                commit.setHash(idNode.asText());
            }

            JsonNode authorTimestampNode = commitNode.get("authorTimestamp");
            if (authorTimestampNode != null) {
                // Convert timestamp to ISO format
                long timestamp = authorTimestampNode.asLong();
                LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
                commit.setDate(dateTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }

            JsonNode messageNode = commitNode.get("message");
            if (messageNode != null) {
                commit.setMessage(messageNode.asText());
            }

            // Parse author for Bitbucket Server
            JsonNode authorNode = commitNode.get("author");
            if (authorNode != null) {
                BitbucketUser author = new BitbucketUser();

                JsonNode nameNode = authorNode.get("name");
                if (nameNode != null) {
                    author.setName(nameNode.asText());
                }

                JsonNode emailNode = authorNode.get("emailAddress");
                if (emailNode != null) {
                    author.setEmailAddress(emailNode.asText());
                }

                JsonNode displayNameNode = authorNode.get("displayName");
                if (displayNameNode != null) {
                    author.setDisplayName(displayNameNode.asText());
                }

                JsonNode idNode2 = authorNode.get("id");
                if (idNode2 != null) {
                    author.setUuid(idNode2.toString());
                }

                JsonNode slugNode = authorNode.get("slug");
                if (slugNode != null) {
                    author.setSlug(slugNode.asText());
                }

                JsonNode activeNode = authorNode.get("active");
                if (activeNode != null) {
                    author.setActive(activeNode.asBoolean());
                }

                commit.setAuthor(author);
            }
        }

        return commit;
    }

    /**
     * Fetches pull requests from a Bitbucket repository.
     */
    public List<BitbucketPullRequest> fetchPullRequests(String owner, String repository, String branchName,
                                                        String username, String appPassword, LocalDateTime since,
                                                        LocalDateTime until, String repositoryUrl) throws PlatformApiException {
        try {
            rateLimitService.checkRateLimit("Bitbucket", username + ":" + appPassword, repository,
                    getApiUrlFromRepoUrl(repositoryUrl));

            WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
            List<BitbucketPullRequest> allPullRequests = new ArrayList<>();
            boolean isBitbucketCloud = repositoryUrl.contains("bitbucket.org");
            StringBuilder urlBuilder;

            if (isBitbucketCloud) {
                urlBuilder = new StringBuilder(String.format("/repositories/%s/%s/pullrequests?state=ALL", owner, repository));
            } else {
                urlBuilder = new StringBuilder(String.format("/projects/%s/repos/%s/pull-requests?state=all", owner, repository));
            }

            String nextUrl = urlBuilder.toString();
            int fetchedCount = 0;

            while (nextUrl != null) {
                logger.debug("Fetching pull requests from: {}", nextUrl);
                String decodedUrl = URLDecoder.decode(nextUrl, StandardCharsets.UTF_8.name());
                String response = client.get()
                        .uri(decodedUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                BitbucketPullRequestsResponse pullRequestsResponse = parsePullRequestsResponse(response, isBitbucketCloud);

                if (pullRequestsResponse.getValues() != null) {
                    for (BitbucketPullRequest pr : pullRequestsResponse.getValues()) {
                        // Filter by branch if specified
                        if (branchName == null || branchName.trim().isEmpty() ||
                                (pr.getDestination() != null && pr.getDestination().getBranch() != null &&
                                        branchName.equals(pr.getDestination().getBranch().getName()))) {

                            // Filter by date if specified
                            if (isPullRequestInDateRange(pr, since, until)) {
                                fetchPullRequestActivity(client, pr);
                                allPullRequests.add(pr);
                                fetchedCount++;
                            }
                        }
                    }
                }

                nextUrl = pullRequestsResponse.getNext();
                if (nextUrl != null && nextUrl.startsWith("http")) {
                    // Extract path from full URL
                    nextUrl = nextUrl.substring(nextUrl.indexOf("/repositories"));
                } else if (nextUrl != null && !isBitbucketCloud) {
                    if (nextUrl.startsWith("nextPageStart=")) {
                        String pageStart = nextUrl.substring("nextPageStart=".length());
                        String baseUrl = urlBuilder.toString();
                        nextUrl = baseUrl + "&start=" + pageStart;
                    }
                }
            }

            logger.info("Fetched {} pull requests from Bitbucket repository {}/{}", allPullRequests.size(), owner, repository);
            return allPullRequests;

        } catch (WebClientResponseException e) {
            logger.error("Error fetching pull requests from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch pull requests from Bitbucket: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Bitbucket response: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to parse Bitbucket response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching pull requests from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    public void fetchPullRequestActivity( WebClient webClient,
                                          BitbucketPullRequest pullRequest) {
        final String EVENT_UPDATE = "update";
        final String EVENT_COMMENT = "comment";
        final String EVENT_APPROVAL = "approval";
        final String EVENT_CHANGES_REQUESTED = "changes_requested";
        final List<String> EVENT_TYPES_API_V2 = Arrays.asList(EVENT_UPDATE, EVENT_COMMENT, EVENT_APPROVAL, EVENT_CHANGES_REQUESTED);

        final String EVENT_RESCOPED = "RESCOPED";
        final String EVENT_COMMENTED = "COMMENTED";
        final String EVENT_APPROVED = "APPROVED";
        final List<String> EVENT_TYPES_API_V1 = Arrays.asList(EVENT_RESCOPED, EVENT_COMMENTED, EVENT_APPROVED);
        String mrUrl = pullRequest.getSelfLink();
        try {
            boolean isBitbucketCloud = mrUrl.contains("bitbucket.org");
            String activityUrl;
            if (isBitbucketCloud) {
                activityUrl = mrUrl+"/activity";
            } else {
                activityUrl = mrUrl+"/activities";
            }

            String nextUrl = activityUrl;

            while (nextUrl != null) {
                logger.debug("Fetching pull requests from: {}", nextUrl);
                String decodedUrl = URLDecoder.decode(nextUrl, StandardCharsets.UTF_8.name());
                String response = webClient.get()
                        .uri(decodedUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                JsonNode jsonNode = objectMapper.readTree(response);
                JsonNode valuesNode = jsonNode.get("values");
                if(isBitbucketCloud) {
                    return;
                } else {
                    final Long[] activityDate = {Long.MAX_VALUE};
                    valuesNode.forEach(value -> {
                        if (EVENT_TYPES_API_V1.contains(value.get("action").asText())) {
                            Long date = value.get("createdDate").asLong();
                            if (date < activityDate[0]) {
                                activityDate[0] = date;
                            }
                        } else if (value.get("action").asText().contains("merge")) {
                            Long date = value.get("createdDate").asLong();
                            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(date / 1000, 0, ZoneOffset.UTC);
                            pullRequest.setPickedUpOn(dateTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                        }
                    });
                    if (activityDate[0] != Long.MAX_VALUE) {
                        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(activityDate[0] / 1000, 0, ZoneOffset.UTC);
                        pullRequest.setPickedUpOn(dateTime.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                    }
                }

                if(!isBitbucketCloud) {
                    if (nextUrl.startsWith("nextPageStart=")) {
                        String pageStart = nextUrl.substring("nextPageStart=".length());
                        String baseUrl = activityUrl;
                        nextUrl = baseUrl + "?start=" + pageStart;
                    } else {
                        nextUrl = null;
                    }
                }
            }

//            logger.info("Fetched {} pull requests from Bitbucket repository {}/{}", allPullRequests.size(), owner, repository);

        } catch (WebClientResponseException e) {
            logger.error("Error fetching pull requests from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch pull requests from Bitbucket: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Bitbucket response: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to parse Bitbucket response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching pull requests from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Parses pull requests response from both Bitbucket Cloud and Server APIs
     */
    private BitbucketPullRequestsResponse parsePullRequestsResponse(String response, boolean isBitbucketCloud) throws JsonProcessingException {
        JsonNode rootNode = objectMapper.readTree(response);
        BitbucketPullRequestsResponse pullRequestsResponse = new BitbucketPullRequestsResponse();
        List<BitbucketPullRequest> pullRequests = new ArrayList<>();

        JsonNode valuesNode = rootNode.get("values");
        if (valuesNode != null && valuesNode.isArray()) {
            for (JsonNode prNode : valuesNode) {
                BitbucketPullRequest pr = parsePullRequestNode(prNode, isBitbucketCloud);
                pullRequests.add(pr);
            }
        }

        pullRequestsResponse.setValues(pullRequests);

        // Handle pagination
        if (isBitbucketCloud) {
            JsonNode nextNode = rootNode.get("next");
            if (nextNode != null && !nextNode.isNull()) {
                pullRequestsResponse.setNext(nextNode.asText());
            }
        } else {
            JsonNode isLastPageNode = rootNode.get("isLastPage");
            JsonNode nextPageStartNode = rootNode.get("nextPageStart");
            if (isLastPageNode != null && !isLastPageNode.asBoolean() && nextPageStartNode != null) {
                pullRequestsResponse.setNext("nextPageStart=" + nextPageStartNode.asInt());
            }
        }

        return pullRequestsResponse;
    }

    /**
     * Parses individual pull request node from both Bitbucket Cloud and Server APIs
     */
    private BitbucketPullRequest parsePullRequestNode(JsonNode prNode, boolean isBitbucketCloud) {
        BitbucketPullRequest pr = new BitbucketPullRequest();

        if (isBitbucketCloud) {
            // Bitbucket Cloud format
            JsonNode idNode = prNode.get("id");
            if (idNode != null) {
                pr.setId(idNode.asLong());
            }

            JsonNode titleNode = prNode.get("title");
            if (titleNode != null) {
                pr.setTitle(titleNode.asText());
            }

            JsonNode descriptionNode = prNode.get("description");
            if (descriptionNode != null) {
                pr.setDescription(descriptionNode.asText());
            }

            JsonNode stateNode = prNode.get("state");
            if (stateNode != null) {
                pr.setState(stateNode.asText());
            }

            JsonNode createdOnNode = prNode.get("created_on");
            if (createdOnNode != null) {
                pr.setCreatedOn(createdOnNode.asText());
            }

            JsonNode updatedOnNode = prNode.get("updated_on");
            if (updatedOnNode != null) {
                pr.setUpdatedOn(updatedOnNode.asText());
            }

            JsonNode mergeCommitNode = prNode.get("merge_commit");
            if (mergeCommitNode != null) {
                BitbucketCommit mergeCommit = objectMapper.convertValue(mergeCommitNode, BitbucketCommit.class);
                pr.setMergeCommit(mergeCommit);
            }

            JsonNode closeSourceBranchNode = prNode.get("close_source_branch");
            if (closeSourceBranchNode != null) {
                pr.setCloseSourceBranch(closeSourceBranchNode.asBoolean());
            }

            JsonNode authorNode = prNode.get("author");
            if (authorNode != null) {
                BitbucketUser author = objectMapper.convertValue(authorNode, BitbucketUser.class);
                pr.setAuthor(author);
            }

            JsonNode reviewersNode = prNode.get("reviewers");
            if (reviewersNode != null && reviewersNode.isArray()) {
                List<BitbucketUser> reviewers = new ArrayList<>();
                for (JsonNode reviewerNode : reviewersNode) {
                    BitbucketUser reviewer = objectMapper.convertValue(reviewerNode, BitbucketUser.class);
                    reviewers.add(reviewer);
                }
                pr.setReviewers(reviewers);
            }

            JsonNode sourceNode = prNode.get("source");
            if (sourceNode != null) {
                BitbucketBranch source = objectMapper.convertValue(sourceNode, BitbucketBranch.class);
                pr.setSource(source);
            }

            JsonNode destinationNode = prNode.get("destination");
            if (destinationNode != null) {
                BitbucketBranch destination = objectMapper.convertValue(destinationNode, BitbucketBranch.class);
                pr.setDestination(destination);
            }
            JsonNode prLinksNode = prNode.get("links");
            if (prLinksNode != null) {
                JsonNode selfNode = prLinksNode.get("self");
                pr.setSelfLink(selfNode.get("href").asText());
            }
        } else {
            // Bitbucket Server format
            JsonNode idNode = prNode.get("id");
            if (idNode != null) {
                pr.setId(idNode.asLong());
            }

            JsonNode titleNode = prNode.get("title");
            if (titleNode != null) {
                pr.setTitle(titleNode.asText());
            }

            JsonNode descriptionNode = prNode.get("description");
            if (descriptionNode != null) {
                pr.setDescription(descriptionNode.asText());
            }

            JsonNode stateNode = prNode.get("state");
            if (stateNode != null) {
                pr.setState(stateNode.asText());
            }

            JsonNode createdDateNode = prNode.get("createdDate");
            if (createdDateNode != null) {
                long timestamp = createdDateNode.asLong();
                LocalDateTime createdDate = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
                pr.setCreatedOn(createdDate.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }

            JsonNode updatedDateNode = prNode.get("updatedDate");
            if (updatedDateNode != null) {
                long timestamp = updatedDateNode.asLong();
                LocalDateTime updatedDate = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
                pr.setUpdatedOn(updatedDate.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }

            JsonNode mergeCommitNode = prNode.get("mergeCommit");
            if (mergeCommitNode != null) {
                BitbucketCommit mergeCommit = objectMapper.convertValue(mergeCommitNode, BitbucketCommit.class);
                pr.setMergeCommit(mergeCommit);
            }

            JsonNode closeSourceBranchNode = prNode.get("closeSourceBranch");
            if (closeSourceBranchNode != null) {
                pr.setCloseSourceBranch(closeSourceBranchNode.asBoolean());
            }

            JsonNode authorNode = prNode.get("author");
            if (authorNode != null) {
                JsonNode userNode = authorNode.get("user");
                if (userNode != null) {
                    BitbucketUser author = objectMapper.convertValue(userNode, BitbucketUser.class);
                    pr.setAuthor(author);
                }
            }

            JsonNode reviewersNode = prNode.get("reviewers");
            if (reviewersNode != null && reviewersNode.isArray()) {
                List<BitbucketUser> reviewers = new ArrayList<>();
                for (JsonNode reviewerNode : reviewersNode) {
                    JsonNode userNode = reviewerNode.get("user");
                    if (userNode != null) {
                        BitbucketUser reviewer = objectMapper.convertValue(userNode, BitbucketUser.class);
                        reviewers.add(reviewer);
                    }
                }
                pr.setReviewers(reviewers);
            }

            JsonNode fromRefNode = prNode.get("fromRef");
            if (fromRefNode != null) {
                BitbucketBranch source = new BitbucketBranch();
                BbBranch branch = new BbBranch();
                branch.setName(fromRefNode.get("displayId").asText());
                source.setBranch(branch);
                pr.setSource(source);
            }

            JsonNode toRefNode = prNode.get("toRef");
            if (toRefNode != null) {
                BitbucketBranch destination = new BitbucketBranch();
                BbBranch branch = new BbBranch();
                branch.setName(toRefNode.get("displayId").asText());
                destination.setBranch(branch);
                pr.setDestination(destination);
            }

            JsonNode linksNode = prNode.get("links");
            if (linksNode != null) {
                JsonNode selfNode = linksNode.get("self");
                if (selfNode != null) {
                    pr.setSelfLink(selfNode.get(0).get("href").asText());
                }
            }
        }

        return pr;
    }

    /**
     * Fetches pull requests by state.
     */
    public List<BitbucketPullRequest> fetchPullRequestsByState(String owner, String repository, String branchName,
                                                               String state, String username, String appPassword,
                                                               LocalDateTime since, LocalDateTime until,
                                                               String repositoryUrl) throws PlatformApiException {
        try {
            rateLimitService.checkRateLimit("Bitbucket", username + ":" + appPassword, repository,
                    getApiUrlFromRepoUrl(repositoryUrl));

            WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
            List<BitbucketPullRequest> allPullRequests = new ArrayList<>();
            String nextUrl = String.format("/repositories/%s/%s/pullrequests", owner, repository);

            // Add state filter
            if (state != null && !state.trim().isEmpty() && !"all".equalsIgnoreCase(state)) {
                nextUrl += "?state=" + state.toUpperCase();
            }

            int fetchedCount = 0;

            while (nextUrl != null) {
                logger.debug("Fetching pull requests by state from: {}", nextUrl);

                String response = client.get()
                        .uri(nextUrl)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                BitbucketPullRequestsResponse pullRequestsResponse = objectMapper.readValue(response, BitbucketPullRequestsResponse.class);

                if (pullRequestsResponse.getValues() != null) {
                    for (BitbucketPullRequest pr : pullRequestsResponse.getValues()) {
                        // Filter by branch if specified
                        if (branchName == null || branchName.trim().isEmpty() ||
                                (pr.getDestination() != null && pr.getDestination().getBranch() != null &&
                                        branchName.equals(pr.getDestination().getBranch().getName()))) {

                            // Filter by date if specified
                            if (isPullRequestInDateRange(pr, since, until)) {
                                allPullRequests.add(pr);
                                fetchedCount++;

                            }
                        }
                    }
                }

                nextUrl = pullRequestsResponse.getNext();
                if (nextUrl != null && nextUrl.startsWith("http")) {
                    // Extract path from full URL
                    nextUrl = nextUrl.substring(nextUrl.indexOf("/repositories"));
                }
            }

            logger.info("Fetched {} pull requests by state '{}' from Bitbucket repository {}/{}",
                    allPullRequests.size(), state, owner, repository);
            return allPullRequests;

        } catch (WebClientResponseException e) {
            logger.error("Error fetching pull requests by state from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch pull requests by state from Bitbucket: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Bitbucket response: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to parse Bitbucket response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching pull requests by state from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches latest pull requests.
     */
    public List<BitbucketPullRequest> fetchLatestPullRequests(String owner, String repository, String branchName,
                                                              String username, String appPassword, int limit,
                                                              String repositoryUrl) throws PlatformApiException {
        try {
            rateLimitService.checkRateLimit("Bitbucket", username + ":" + appPassword, repository,
                    getApiUrlFromRepoUrl(repositoryUrl));

            WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
            List<BitbucketPullRequest> pullRequests = new ArrayList<>();
            String url = String.format("/repositories/%s/%s/pullrequests?pagelen=%d", owner, repository, Math.min(limit, 50));

            logger.debug("Fetching latest pull requests from: {}", url);

            String response = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            BitbucketPullRequestsResponse pullRequestsResponse = objectMapper.readValue(response, BitbucketPullRequestsResponse.class);

            if (pullRequestsResponse.getValues() != null) {
                for (BitbucketPullRequest pr : pullRequestsResponse.getValues()) {
                    // Filter by branch if specified
                    if (branchName == null || branchName.trim().isEmpty() ||
                            (pr.getDestination() != null && pr.getDestination().getBranch() != null &&
                                    branchName.equals(pr.getDestination().getBranch().getName()))) {

                        pullRequests.add(pr);

                        if (pullRequests.size() >= limit) {
                            break;
                        }
                    }
                }
            }

            logger.info("Fetched {} latest pull requests from Bitbucket repository {}/{}",
                    pullRequests.size(), owner, repository);
            return pullRequests;

        } catch (WebClientResponseException e) {
            logger.error("Error fetching latest pull requests from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch latest pull requests from Bitbucket: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            logger.error("Error parsing Bitbucket response: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to parse Bitbucket response: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching latest pull requests from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Tests the connection to Bitbucket.
     */
    public boolean testConnection(String username, String appPassword) {
        try {
            WebClient client = getBitbucketClient(username, appPassword, defaultBitbucketApiUrl);

            String response = client.get()
                    .uri("/user")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response != null && !response.trim().isEmpty();

        } catch (Exception e) {
            logger.error("Failed to test Bitbucket connection: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Gets the API URL.
     */
    public String getApiUrl() {
        return defaultBitbucketApiUrl;
    }

    /**
     * Gets the API URL from repository URL.
     */
    public String getApiUrlFromRepoUrl(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.trim().isEmpty()) {
            return defaultBitbucketApiUrl;
        }

        // Check if it's Bitbucket Cloud
        if (repositoryUrl.contains("bitbucket.org")) {
            return "https://api.bitbucket.org/2.0";
        }

        // For Bitbucket Server (on-premise), extract the base URL
        try {
            String baseUrl = repositoryUrl;
            if (baseUrl.contains("/scm/")) {
                // Bitbucket Server URL format: https://bitbucket.company.com/scm/project/repo.git
                baseUrl = baseUrl.substring(0, baseUrl.indexOf("/scm/"));
            } else if (baseUrl.contains("/projects/")) {
                // Alternative format: https://bitbucket.company.com/projects/project/repos/repo
                baseUrl = baseUrl.substring(0, baseUrl.indexOf("/projects/"));
            }

            // Remove .git suffix if present
            if (baseUrl.endsWith(".git")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 4);
            }

            // Remove trailing slash
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            return baseUrl + "/rest/api/1.0";

        } catch (Exception e) {
            logger.warn("Failed to extract API URL from repository URL: {}. Using default.", repositoryUrl);
            return defaultBitbucketApiUrl;
        }
    }

    /**
     * Fetches commit diffs from Bitbucket.
     */
    public String fetchCommitDiffs(String owner, String repository, String commitSha,
                                   String username, String appPassword, String repositoryUrl) throws PlatformApiException {
        try {
            WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
            String url;
            boolean isBitbucketCloud = repositoryUrl.contains("bitbucket.org");

            if (isBitbucketCloud) {
                url = String.format("/repositories/%s/%s/diff/%s", owner, repository, commitSha);
            } else {
                url = String.format("/projects/%s/repos/%s/commits/%s/diff", owner, repository, commitSha);
            }

            return executeWithRedirectHandling(client, url, username, appPassword);

        } catch (WebClientResponseException e) {
            logger.error("Error fetching commit diffs from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch commit diffs from Bitbucket: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching commit diffs from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Fetches pull request diffs from Bitbucket.
     */
    /**
     * Fetches pull request diffs from Bitbucket.
     */
    public String fetchPullRequestDiffs(String owner, String repository, Long pullRequestId,
                                        String username, String appPassword, String repositoryUrl) throws PlatformApiException {
        try {
            WebClient client = getBitbucketClientFromRepoUrl(username, appPassword, repositoryUrl);
            String url;
            boolean isBitbucketCloud = repositoryUrl.contains("bitbucket.org");

            if (isBitbucketCloud) {
                url = String.format("/repositories/%s/%s/pullrequests/%d/diff", owner, repository, pullRequestId);
            } else {
                url = String.format("/projects/%s/repos/%s/pull-requests/%s/diff", owner, repository, pullRequestId);
            }
            return executeWithRedirectHandling(client, url, username, appPassword);

        } catch (WebClientResponseException e) {
            logger.error("Error fetching pull request diffs from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Failed to fetch pull request diffs from Bitbucket: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected error fetching pull request diffs from Bitbucket: {}", e.getMessage());
            throw new PlatformApiException("Bitbucket", "Unexpected error: " + e.getMessage(), e);
        }
    }

    // Helper methods

    private boolean isCommitInDateRange(BitbucketCommit commit, LocalDateTime since, LocalDateTime until) {
        if (commit.getDate() == null) {
            return true; // Include commits without date
        }

        try {
            LocalDateTime commitDate;

            // Handle both Bitbucket Cloud (ISO format) and Server (timestamp) formats
            if (commit.getDate().contains("T")) {
                // Bitbucket Cloud format: ISO date-time
                commitDate = LocalDateTime.parse(commit.getDate(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } else {
                // Fallback: try to parse as timestamp if it's numeric
                try {
                    long timestamp = Long.parseLong(commit.getDate());
                    commitDate = LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
                } catch (NumberFormatException e) {
                    // If not numeric, try ISO format anyway
                    commitDate = LocalDateTime.parse(commit.getDate(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
            }

            if (since != null && commitDate.isBefore(since)) {
                return false;
            }

            if (until != null && commitDate.isAfter(until)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warn("Failed to parse commit date: {}. Including commit in results.", commit.getDate());
            return true; // Include commits with unparseable dates
        }
    }

    private boolean isPullRequestInDateRange(BitbucketPullRequest pr, LocalDateTime since, LocalDateTime until) {
        if (pr.getUpdatedOn() == null) {
            return true; // Include PRs without date
        }

        try {
            LocalDateTime prDate = LocalDateTime.parse(pr.getUpdatedOn(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            if (since != null && prDate.isBefore(since)) {
                return false;
            }

            if (until != null && prDate.isAfter(until)) {
                return false;
            }

            return true;
        } catch (Exception e) {
            logger.warn("Failed to parse pull request date: {}", pr.getUpdatedOn());
            return true; // Include PRs with unparseable dates
        }
    }

    /**
     * Executes a WebClient GET request with automatic redirect handling.
     * This method handles HTTP 302 redirects by following the Location header.
     */
    private String executeWithRedirectHandling(WebClient client, String uri, String username, String appPassword) {
        return client.get()
                .uri(uri)
                .exchangeToMono(response -> {
                    if (response.statusCode().is3xxRedirection()) {
                        String location = response.headers().header("Location").stream().findFirst().orElse(null);
                        if (location != null) {
                            logger.info("Following redirect to: {}", location);

                            try {
                                location = URLDecoder.decode(location, StandardCharsets.UTF_8.name()).trim();
                                if (!location.startsWith("http")) {
                                    logger.warn("Invalid redirect URL: {}", location);
                                    return Mono.empty();
                                }
                            } catch (Exception e) {
                                logger.error("Failed to process redirect URL: {}", location, e);
                                return Mono.empty();
                            }

                            String credentials = Base64.getEncoder().encodeToString((username + ":" + appPassword).getBytes());
                            WebClient redirectClient = webClientBuilder
                                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
                                    .build();

                            return redirectClient.get()
                                    .uri(location)
                                    .retrieve()
                                    .bodyToFlux(DataBuffer.class) // Stream response as DataBuffer
                                    .map(dataBuffer -> {
                                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                        dataBuffer.read(bytes);
                                        DataBufferUtils.release(dataBuffer); // Release buffer
                                        return new String(bytes, StandardCharsets.UTF_8);
                                    })
                                    .reduce((chunk1, chunk2) -> chunk1 + chunk2) // Combine chunks
                                    .onErrorResume(e -> {
                                        logger.error("Error streaming redirect response: {}", e.getMessage());
                                        return Mono.empty();
                                    });
                        } else {
                            logger.warn("Redirect response received but no Location header found");
                            return Mono.empty();
                        }
                    } else if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToFlux(DataBuffer.class) // Stream response as DataBuffer
                                .map(dataBuffer -> {
                                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(bytes);
                                    DataBufferUtils.release(dataBuffer); // Release buffer
                                    return new String(bytes, StandardCharsets.UTF_8);
                                })
                                .reduce((chunk1, chunk2) -> chunk1 + chunk2) // Combine chunks
                                .onErrorResume(e -> {
                                    logger.error("Error streaming response: {}", e.getMessage());
                                    return Mono.empty();
                                });
                    } else {
                        logger.error("Unexpected response status: {}", response.statusCode());
                        return response.createException().flatMap(Mono::error);
                    }
                })
                .block();
    }

    public BitbucketParser getBitbucketParser(String repositoryUrl) {
        if (repositoryUrl.contains("bitbucket.org")) {
            return new CloudBitBucketParser();
        } else {
            return new ServerBitbucketParser();
        }
    }

    // Data classes for Bitbucket API responses

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketCommitsResponse {
        private List<BitbucketCommit> values;
        private String next;

        public List<BitbucketCommit> getValues() { return values; }
        public void setValues(List<BitbucketCommit> values) { this.values = values; }
        public String getNext() { return next; }
        public void setNext(String next) { this.next = next; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketPullRequestsResponse {
        private List<BitbucketPullRequest> values;
        private String next;

        public List<BitbucketPullRequest> getValues() { return values; }
        public void setValues(List<BitbucketPullRequest> values) { this.values = values; }
        public String getNext() { return next; }
        public void setNext(String next) { this.next = next; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketCommit {
        private String hash;
        private String message;
        private String date;
        private BitbucketUser author;
        private BitbucketCommitStats stats;

        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public BitbucketUser getAuthor() { return author; }
        public void setAuthor(BitbucketUser author) { this.author = author; }
        public BitbucketCommitStats getStats() { return stats; }
        public void setStats(BitbucketCommitStats stats) { this.stats = stats; }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketPullRequest {
        private Long id;
        private String title;
        private String description;
        private String state;
        @JsonProperty("created_on")
        private String createdOn;
        @JsonProperty("updated_on")
        private String updatedOn;
        @JsonProperty("merge_commit")
        private BitbucketCommit mergeCommit;
        @JsonProperty("close_source_branch")
        private Boolean closeSourceBranch;
        private BitbucketUser author;
        private List<BitbucketUser> reviewers;
        private BitbucketBranch source;
        private BitbucketBranch destination;
        private String selfLink;
        private String pickedUpOn;
        private String mergedOn;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketUser {
        private String type;
        private BbUser user;
        @JsonProperty("display_name")
        private String displayName;
        private String uuid;
        @JsonProperty("account_id")
        private String accountId;
        private String nickname;

        // Additional fields for Bitbucket Server
        private String name;
        private String emailAddress;
        private Integer serverId;
        private String slug;
        private Boolean active;

        public BbUser getUser() { return user; }
        public void setUser(BbUser user) { this.user = user; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }

        // Getters and setters for Bitbucket Server fields
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmailAddress() { return emailAddress; }
        public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }
        public Integer getServerId() { return serverId; }
        public void setServerId(Integer serverId) { this.serverId = serverId; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
    }

    public static class BbUser {
        @JsonProperty("display_name")
        private String displayName;
        private String username;
        private String uuid;
        @JsonProperty("account_id")
        private String accountId;
        private String nickname;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketBranch {
        private BbBranch branch;
        private BitbucketCommit commit;

        public BbBranch getBranch() { return branch; }
        public void setBranch(BbBranch branch) { this.branch = branch; }
        public BitbucketCommit getCommit() { return commit; }
        public void setCommit(BitbucketCommit commit) { this.commit = commit; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BbBranch {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BitbucketCommitStats {
        private Integer total;
        private Integer additions;
        private Integer deletions;

        public Integer getTotal() { return total; }
        public void setTotal(Integer total) { this.total = total; }
        public Integer getAdditions() { return additions; }
        public void setAdditions(Integer additions) { this.additions = additions; }
        public Integer getDeletions() { return deletions; }
        public void setDeletions(Integer deletions) { this.deletions = deletions; }
    }
}