package com.baseline.timetrack.integration.bitrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.baseline.timetrack.config.AppProperties.Integrations.Bitrix24;
import com.baseline.timetrack.entity.TaskStatus;
import com.baseline.timetrack.entity.TimerType;
import com.baseline.timetrack.integration.ConnectorException;
import com.baseline.timetrack.integration.ExternalTask;
import com.baseline.timetrack.integration.TimeEntryExport;

class Bitrix24ConnectorTest {

    private static final String HOOK = "https://acme.bitrix24.com/rest/1/secret";

    private MockRestServiceServer server;
    private Bitrix24Connector connector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        connector = new Bitrix24Connector(new Bitrix24(true, HOOK, "UF_AUTO_SITE", true, Duration.ofSeconds(5)), builder);
    }

    @Test
    void fetchesAllPagesAndMapsTasks() {
        server.expect(requestTo(HOOK + "/tasks.task.list.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"filter":{"RESPONSIBLE_ID":"7","<REAL_STATUS":5},"start":0,
                         "select":["ID","TITLE","DESCRIPTION","STATUS","REAL_STATUS","DEADLINE","RESPONSIBLE_ID","ACCOMPLICES","UF_AUTO_SITE"]}
                        """))
                .andRespond(withSuccess("""
                        {"result":{"tasks":[
                          {"id":"101","title":"Install AP","description":"2nd floor","status":"2","realStatus":"3",
                           "deadline":"2026-10-25T18:00:00+03:00","responsibleId":"7","accomplices":["8"],"ufAutoSite":"BRANCH-12"}
                        ]},"next":50,"total":51}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(HOOK + "/tasks.task.list.json"))
                .andExpect(content().json("{\"start\":50}"))
                .andRespond(withSuccess("""
                        {"result":{"tasks":[{"id":"102","title":"Closed one","status":"5","responsibleId":"7"}]},"total":51}
                        """, MediaType.APPLICATION_JSON));

        List<ExternalTask> tasks = connector.fetchOpenTasks("7");

        server.verify();
        assertThat(tasks).singleElement().satisfies(t -> {
            assertThat(t.externalId()).isEqualTo("101");
            assertThat(t.status()).isEqualTo(TaskStatus.IN_PROGRESS);
            assertThat(t.dueAt()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
            assertThat(t.assigneeExternalUserIds()).containsExactly("7", "8");
            assertThat(t.site().externalId()).isEqualTo("BRANCH-12");
        });
    }

    @Test
    void pushesElapsedTime() {
        server.expect(requestTo(HOOK + "/task.elapseditem.add.json"))
                .andExpect(content().json("""
                        {"TASKID":"101","ARFIELDS":{"SECONDS":2700,"COMMENT_TEXT":"Replaced filter","USER_ID":"7"}}
                        """))
                .andRespond(withSuccess("{\"result\":555}", MediaType.APPLICATION_JSON));

        var id = connector.pushTimeEntry(entry(2700));

        server.verify();
        assertThat(id).contains("555");
    }

    @Test
    void skipsZeroSecondEntries() {
        assertThat(connector.pushTimeEntry(entry(0))).isEmpty();
        server.verify();
    }

    @Test
    void reportsBitrixErrors() {
        server.expect(requestTo(HOOK + "/task.elapseditem.add.json"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"ACCESS_DENIED\",\"error_description\":\"Access denied\"}"));

        assertThatThrownBy(() -> connector.pushTimeEntry(entry(60)))
                .isInstanceOf(ConnectorException.class)
                .hasMessageContaining("ACCESS_DENIED");
    }

    @Test
    void mapsStatusCodes() {
        assertThat(Bitrix24Connector.mapStatus("1")).isEqualTo(TaskStatus.OPEN);
        assertThat(Bitrix24Connector.mapStatus("4")).isEqualTo(TaskStatus.DONE);
        assertThat(Bitrix24Connector.mapStatus("7")).isEqualTo(TaskStatus.CANCELLED);
        assertThat(Bitrix24Connector.toCamelCase("UF_AUTO_123456")).isEqualTo("ufAuto123456");
    }

    private static TimeEntryExport entry(long seconds) {
        return new TimeEntryExport(1L, "bitrix24", "101", "7", "tech@example.com", TimerType.TASK,
                Instant.parse("2026-10-23T10:00:00Z"), Instant.parse("2026-10-23T10:45:00Z"), seconds,
                "Replaced filter", null, null, null, Instant.now());
    }
}
