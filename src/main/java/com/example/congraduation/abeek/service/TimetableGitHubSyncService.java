package com.example.congraduation.abeek.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;

/**
 * 관리자 업로드 JSON을 GitHub Contents API로 커밋한다.
 * (로컬 git push가 아니라 API로 원격 저장소 파일을 갱신)
 */
@Slf4j
@Service
public class TimetableGitHubSyncService {

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String token;
    private final String owner;
    private final String repo;
    private final String branch;
    private final String pathPrefix;
    private final RestClient restClient;

    @Autowired
    public TimetableGitHubSyncService(
            ObjectMapper objectMapper,
            @Value("${app.timetable.github.enabled:true}") boolean enabled,
            @Value("${app.timetable.github.token:}") String token,
            @Value("${app.timetable.github.owner:congraduation-team}") String owner,
            @Value("${app.timetable.github.repo:congraduation-backend}") String repo,
            @Value("${app.timetable.github.branch:main}") String branch,
            @Value("${app.timetable.github.path-prefix:src/main/resources/timetable-data}") String pathPrefix
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
        this.pathPrefix = pathPrefix.endsWith("/")
                ? pathPrefix.substring(0, pathPrefix.length() - 1)
                : pathPrefix;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    /** 테스트용 */
    TimetableGitHubSyncService(
            ObjectMapper objectMapper,
            boolean enabled,
            String token,
            String owner,
            String repo,
            String branch,
            String pathPrefix,
            RestClient restClient
    ) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
        this.pathPrefix = pathPrefix;
        this.restClient = restClient;
    }

    public SyncResult upsertTimetableFile(String fileName, byte[] utf8JsonBytes) {
        if (!enabled) {
            return SyncResult.skipped("GitHub 동기화가 비활성화되어 있습니다 (app.timetable.github.enabled=false).");
        }
        if (token.isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub 자동 반영을 위해 환경변수 TIMETABLE_GITHUB_TOKEN 을 설정하세요. "
                            + "(repo Contents 읽기/쓰기 권한 PAT, app.timetable.github.enabled=true)"
            );
        }

        String path = pathPrefix + "/" + fileName;
        String apiPath = "/repos/" + owner + "/" + repo + "/contents/" + path;
        String sha = fetchExistingSha(apiPath);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("message", "CHORE: update timetable " + fileName.replace(".json", ""));
        body.put("content", Base64.getEncoder().encodeToString(utf8JsonBytes));
        body.put("branch", branch);
        if (sha != null && !sha.isBlank()) {
            body.put("sha", sha);
        }

        try {
            JsonNode response = restClient.put()
                    .uri(apiPath)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            String commitUrl = null;
            if (response != null && response.path("commit").hasNonNull("html_url")) {
                commitUrl = response.path("commit").path("html_url").asText();
            }
            String htmlUrl = response != null && response.hasNonNull("content")
                    ? response.path("content").path("html_url").asText(null)
                    : null;
            log.info("Pushed timetable {} to GitHub ({}/{}@{})", fileName, owner, repo, branch);
            return SyncResult.synced(commitUrl != null ? commitUrl : htmlUrl);
        } catch (RestClientResponseException e) {
            throw new IllegalArgumentException(
                    "GitHub 시간표 반영 실패 (" + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("GitHub 시간표 반영 실패: " + e.getMessage(), e);
        }
    }

    private String fetchExistingSha(String apiPath) {
        try {
            JsonNode existing = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(apiPath).queryParam("ref", branch).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            if (existing != null && existing.hasNonNull("sha")) {
                return existing.get("sha").asText();
            }
            return null;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw new IllegalArgumentException(
                    "GitHub 기존 파일 조회 실패 (" + e.getStatusCode().value() + "): "
                            + e.getResponseBodyAsString(),
                    e
            );
        }
    }

    public record SyncResult(boolean synced, String commitUrl, String detail) {
        static SyncResult synced(String commitUrl) {
            return new SyncResult(true, commitUrl, "GitHub에 반영되었습니다.");
        }

        static SyncResult skipped(String detail) {
            return new SyncResult(false, null, detail);
        }
    }
}
