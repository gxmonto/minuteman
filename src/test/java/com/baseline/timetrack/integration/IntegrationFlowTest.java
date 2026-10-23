package com.baseline.timetrack.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.baseline.timetrack.entity.SyncStatus;
import com.baseline.timetrack.entity.TaskStatus;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.repository.TimerEntryRepository;
import com.baseline.timetrack.support.ApiTestBase;
import com.baseline.timetrack.support.FakeConnector;
import com.jayway.jsonpath.JsonPath;

class IntegrationFlowTest extends ApiTestBase {

    @Autowired
    FakeConnector fakeCrm;
    @Autowired
    TimeEntrySyncService timeEntrySync;
    @Autowired
    TimerEntryRepository timers;

    private String link(String admin, Tech tech, String provider) throws Exception {
        String externalId = "ext-" + UUID.randomUUID();
        mvc.perform(json(put("/api/admin/users/" + tech.user().getId() + "/external-accounts/" + provider), admin,
                        "{\"externalUserId\":\"" + externalId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedAccounts[0].provider").value(provider));
        return externalId;
    }

    @Test
    void pulledTasksAreTimedAndPushedBackThroughTheOutbox() throws Exception {
        String admin = newAdminToken();
        Tech tech = newTech();
        String externalUserId = link(admin, tech, "fakecrm");
        String taskId = "T-" + UUID.randomUUID();
        fakeCrm.tasksByUser.put(externalUserId, List.of(new ExternalTask(taskId, "Fix AC unit", "Room 4",
                TaskStatus.OPEN, null, null,
                new ExternalTask.ExternalSite("S-" + taskId, "Hospital", "Main St 1", 19.0, -99.0, 120))));

        mvc.perform(auth(post("/api/admin/integrations/fakecrm/sync"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksImported").value(1));

        String tasksJson = mvc.perform(auth(get("/api/tasks"), tech.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalId").value(taskId))
                .andExpect(jsonPath("$[0].site.radiusMeters").value(120))
                .andReturn().getResponse().getContentAsString();
        int localTaskId = JsonPath.read(tasksJson, "$[0].id");

        mvc.perform(auth(post("/api/work/day/start"), tech.token())).andExpect(status().isOk());
        String started = mvc.perform(json(post("/api/work/timers/start"), tech.token(),
                        "{\"type\":\"TASK\",\"taskId\":" + localTaskId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long timerId = ((Number) JsonPath.read(started, "$.id")).longValue();
        clock.advance(Duration.ofMinutes(45));

        // The external system is down: the stop still succeeds and the entry waits in the outbox.
        fakeCrm.failPushes = true;
        mvc.perform(json(post("/api/work/timers/stop"), tech.token(), "{\"notes\":\"Replaced filter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncStatus").value("PENDING"));
        timeEntrySync.pushDue();
        TimerEntry afterFailure = timers.findById(timerId).orElseThrow();
        assertThat(afterFailure.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(afterFailure.getSyncAttempts()).isEqualTo(1);
        assertThat(afterFailure.getNextSyncAt()).isAfter(clock.instant());

        // Back up, and the backoff has elapsed.
        fakeCrm.failPushes = false;
        clock.advance(Duration.ofMinutes(5));
        timeEntrySync.pushDue();
        TimerEntry synced = timers.findById(timerId).orElseThrow();
        assertThat(synced.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(synced.getExternalId()).isEqualTo("ext-" + timerId);
        assertThat(fakeCrm.pushed).filteredOn(e -> e.id() == timerId).singleElement().satisfies(e -> {
            assertThat(e.taskExternalId()).isEqualTo(taskId);
            assertThat(e.userExternalId()).isEqualTo(externalUserId);
            assertThat(e.durationSeconds()).isEqualTo(2700);
            assertThat(e.notes()).isEqualTo("Replaced filter");
        });

        // Task disappears from the user's open list (closed in the CRM): no longer offered in the app.
        fakeCrm.tasksByUser.put(externalUserId, List.of());
        mvc.perform(auth(post("/api/tasks/refresh"), tech.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void outboxGivesUpAfterMaxAttemptsAndAdminCanRetry() throws Exception {
        String admin = newAdminToken();
        Tech tech = newTech();
        String externalUserId = link(admin, tech, "fakecrm");
        fakeCrm.tasksByUser.put(externalUserId, List.of(new ExternalTask("R-" + UUID.randomUUID(), "Retry me",
                null, null, null, null, null)));
        mvc.perform(auth(post("/api/tasks/refresh"), tech.token())).andExpect(status().isOk());
        Long taskId = tasks.findAssignedFromSource(tech.user().getId(), "fakecrm").getFirst().getId();

        mvc.perform(auth(post("/api/work/day/start"), tech.token())).andExpect(status().isOk());
        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TASK\",\"taskId\":" + taskId + "}"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        String stopped = mvc.perform(auth(post("/api/work/timers/stop"), tech.token()))
                .andReturn().getResponse().getContentAsString();
        long timerId = ((Number) JsonPath.read(stopped, "$.id")).longValue();

        fakeCrm.failPushes = true;
        for (int i = 0; i < 3; i++) {
            timeEntrySync.pushOne(timerId);
        }
        assertThat(timers.findById(timerId).orElseThrow().getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        mvc.perform(auth(get("/api/admin/time-entries/sync-failures"), admin))
                .andExpect(jsonPath("$[?(@.timerId == " + timerId + ")].lastError").value("fakecrm is down"));

        fakeCrm.failPushes = false;
        mvc.perform(auth(post("/api/admin/time-entries/" + timerId + "/retry-sync"), admin))
                .andExpect(status().isAccepted());
        assertThat(timers.findById(timerId).orElseThrow().getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
    }

    @Test
    void inboundApiLetsAnySystemPushTasksAndPullTimeEntries() throws Exception {
        String admin = newAdminToken();
        Tech tech = newTech();
        String externalUserId = link(admin, tech, "acme");
        String taskId = "A-" + UUID.randomUUID();
        String batch = """
                {"tasks":[{"externalId":"%s","title":"Inspect panel","assigneeExternalUserIds":["%s","unknown-user"],
                  "site":{"externalId":"site-%s","name":"Plant 2","latitude":20.5,"longitude":-100.4}}]}
                """.formatted(taskId, externalUserId, taskId);

        mvc.perform(put("/api/integrations/inbound/acme/tasks").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/integrations/inbound/acme/tasks").header("X-Api-Key", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/integrations/inbound/acme/tasks").header("X-Api-Key", "other-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/integrations/inbound/acme/tasks").header("X-Api-Key", "acme-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tasks\":[{\"title\":\"no id\"}]}"))
                .andExpect(status().isBadRequest());
        // A user JWT is not an API key.
        mvc.perform(json(put("/api/integrations/inbound/acme/tasks"), tech.token(), batch))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/integrations/inbound/acme/tasks").header("X-Api-Key", "acme-test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        String tasksJson = mvc.perform(auth(get("/api/tasks"), tech.token()))
                .andExpect(jsonPath("$[0].title").value("Inspect panel"))
                .andExpect(jsonPath("$[0].site.radiusMeters").value(150))
                .andReturn().getResponse().getContentAsString();
        int localTaskId = JsonPath.read(tasksJson, "$[0].id");

        mvc.perform(auth(post("/api/work/day/start"), tech.token())).andExpect(status().isOk());
        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TASK\",\"taskId\":" + localTaskId + "}"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(20));
        // No connector for acme: nothing to push, acme pulls instead.
        mvc.perform(auth(post("/api/work/timers/stop"), tech.token()))
                .andExpect(jsonPath("$.syncStatus").value("NOT_REQUIRED"));

        String page = mvc.perform(get("/api/integrations/inbound/acme/time-entries").header("X-Api-Key", "acme-test-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> pageTaskIds = JsonPath.read(page, "$.items[*].taskExternalId");
        assertThat(pageTaskIds).contains(taskId);
        String cursor = JsonPath.read(page, "$.nextCursor");

        mvc.perform(get("/api/integrations/inbound/acme/time-entries").param("cursor", cursor)
                        .header("X-Api-Key", "acme-test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/integrations/inbound/acme/time-entries").param("cursor", "garbage")
                        .header("X-Api-Key", "acme-test-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminGeofenceSurvivesProviderSyncs() throws Exception {
        String admin = newAdminToken();
        Tech tech = newTech();
        String externalUserId = link(admin, tech, "fakecrm");
        String siteExternalId = "G-" + UUID.randomUUID();
        ExternalTask task = new ExternalTask("GT-" + siteExternalId, "Geofenced", null, null, null, null,
                new ExternalTask.ExternalSite(siteExternalId, "Mall", null, null, null, null));
        fakeCrm.tasksByUser.put(externalUserId, List.of(task));
        mvc.perform(auth(post("/api/tasks/refresh"), tech.token())).andExpect(status().isOk());
        Long siteId = sites.findBySourceAndExternalId("fakecrm", siteExternalId).orElseThrow().getId();

        mvc.perform(json(put("/api/admin/sites/" + siteId + "/geofence"), admin,
                        "{\"latitude\":25.1,\"longitude\":-80.2,\"radiusMeters\":60}"))
                .andExpect(status().isOk());

        fakeCrm.tasksByUser.put(externalUserId, List.of(new ExternalTask(task.externalId(), "Geofenced", null, null,
                null, null, new ExternalTask.ExternalSite(siteExternalId, "Mall", null, 1.0, 1.0, 999))));
        mvc.perform(auth(post("/api/tasks/refresh"), tech.token()))
                .andExpect(jsonPath("$[0].site.latitude").value(25.1))
                .andExpect(jsonPath("$[0].site.radiusMeters").value(60));
    }
}
