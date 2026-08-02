package com.example.congraduation.abeek.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;

class TimetableGitHubSyncServiceTest {

    @Test
    void requiresTokenWhenEnabled() {
        TimetableGitHubSyncService service = new TimetableGitHubSyncService(
                new ObjectMapper(),
                true,
                "",
                "owner",
                "repo",
                "main",
                "src/main/resources/timetable-data",
                RestClient.create()
        );
        assertThatThrownBy(() -> service.upsertTimetableFile("2026-1.json", "{}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TIMETABLE_GITHUB_TOKEN");
    }

    @Test
    void createsNewFileWhenMissing() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        TimetableGitHubSyncService service = new TimetableGitHubSyncService(
                new ObjectMapper(),
                true,
                "test-token",
                "congraduation-team",
                "congraduation-backend",
                "main",
                "src/main/resources/timetable-data",
                client
        );

        server.expect(requestTo("https://api.github.com/repos/congraduation-team/congraduation-backend/contents/src/main/resources/timetable-data/2026-2.json?ref=main"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withResourceNotFound());

        server.expect(requestTo("https://api.github.com/repos/congraduation-team/congraduation-backend/contents/src/main/resources/timetable-data/2026-2.json"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess("""
                        {"commit":{"html_url":"https://github.com/congraduation-team/congraduation-backend/commit/abc"},"content":{"html_url":"https://github.com/x"}}
                        """, MediaType.APPLICATION_JSON));

        TimetableGitHubSyncService.SyncResult result = service.upsertTimetableFile(
                "2026-2.json",
                "{\"termYear\":2026}".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.synced()).isTrue();
        assertThat(result.commitUrl()).contains("/commit/abc");
        server.verify();
    }
}
