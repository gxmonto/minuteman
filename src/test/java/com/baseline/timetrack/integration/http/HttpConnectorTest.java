package com.baseline.timetrack.integration.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.baseline.timetrack.config.AppProperties.Integrations.HttpConnectorProperties;
import com.baseline.timetrack.entity.TaskStatus;
import com.baseline.timetrack.entity.TimerType;
import com.baseline.timetrack.integration.ConnectorException;
import com.baseline.timetrack.integration.TimeEntryExport;

class HttpConnectorTest {

    private MockRestServiceServer server;
    private HttpConnector connector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        connector = new HttpConnector("myerp", new HttpConnectorProperties(
                "https://erp.example.com/tasks", "https://erp.example.com/time-entries",
                Map.of("Authorization", "Bearer abc"), Duration.ofSeconds(5)), builder);
    }

    @Test
    void fetchesTasksForUser() {
        server.expect(requestTo("https://erp.example.com/tasks?externalUserId=u%201"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("""
                        [{"externalId":"9","title":"Repair","status":"IN_PROGRESS","extra":"ignored",
                          "site":{"externalId":"s9","latitude":1.5,"longitude":2.5,"radiusMeters":75}}]
                        """, MediaType.APPLICATION_JSON));

        var tasks = connector.fetchOpenTasks("u 1");

        assertThat(tasks).singleElement().satisfies(t -> {
            assertThat(t.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(t.site().radiusMeters()).isEqualTo(75);
        });
    }

    @Test
    void pushesTimeEntries() {
        server.expect(requestTo("https://erp.example.com/time-entries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"id\":3,\"taskExternalId\":\"9\",\"durationSeconds\":600,\"type\":\"TASK\"}"))
                .andRespond(withSuccess("{\"id\":\"erp-77\"}", MediaType.APPLICATION_JSON));

        assertThat(connector.pushTimeEntry(entry())).contains("erp-77");
        assertThat(connector.supportsTimeEntryPush()).isTrue();
    }

    @Test
    void acceptsEmptyPushResponses() {
        server.expect(requestTo("https://erp.example.com/time-entries")).andRespond(withSuccess());
        assertThat(connector.pushTimeEntry(entry())).isEmpty();
    }

    @Test
    void wrapsFailures() {
        server.expect(requestTo("https://erp.example.com/time-entries")).andRespond(withServerError());
        assertThatThrownBy(() -> connector.pushTimeEntry(entry())).isInstanceOf(ConnectorException.class);
    }

    private static TimeEntryExport entry() {
        return new TimeEntryExport(3L, "myerp", "9", "u1", "t@example.com", TimerType.TASK,
                Instant.parse("2026-10-23T10:00:00Z"), Instant.parse("2026-10-23T10:10:00Z"), 600, null,
                null, null, null, Instant.parse("2026-10-23T10:10:00Z"));
    }
}
