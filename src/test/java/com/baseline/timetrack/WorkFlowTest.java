package com.baseline.timetrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.baseline.timetrack.entity.Role;
import com.baseline.timetrack.entity.Site;
import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.WorkDay;
import com.baseline.timetrack.notification.ReminderService;
import com.baseline.timetrack.repository.WorkDayRepository;
import com.baseline.timetrack.support.ApiTestBase;

class WorkFlowTest extends ApiTestBase {

    @Autowired
    ReminderService reminders;
    @Autowired
    WorkDayRepository workDays;

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/work/current")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deactivatedUserIsRejectedEvenWithValidToken() throws Exception {
        Tech tech = newTech();
        tech.user().setActive(false);
        users.save(tech.user());
        mvc.perform(auth(get("/api/work/current"), tech.token())).andExpect(status().isUnauthorized());
    }

    @Test
    void techniciansCannotUseAdminApi() throws Exception {
        Tech tech = newTech();
        mvc.perform(auth(get("/api/admin/users"), tech.token())).andExpect(status().isForbidden());
    }

    @Test
    void fullDayWithTaskTimer() throws Exception {
        Tech tech = newTech();
        Site site = createSite(19.4326, -99.1332, 100);
        Task task = createTask(site, tech.user());

        mvc.perform(json(post("/api/work/day/start"), tech.token(),
                        "{\"location\":{\"latitude\":19.4326,\"longitude\":-99.1332}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                // Entities are never serialized directly anymore.
                .andExpect(content().string(not(containsString("password"))));

        mvc.perform(json(post("/api/work/timers/start"), tech.token(),
                        "{\"type\":\"TASK\",\"taskId\":" + task.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskTitle").value("Install router"))
                .andExpect(jsonPath("$.siteId").value(site.getId()));

        clock.advance(Duration.ofMinutes(30));
        mvc.perform(auth(get("/api/work/current"), tech.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDayOpen").value(true))
                .andExpect(jsonPath("$.timer.taskId").value(task.getId()))
                .andExpect(jsonPath("$.secondsRunning").value(1800))
                .andExpect(jsonPath("$.totalSecondsToday").value(1800));

        mvc.perform(json(post("/api/work/timers/stop"), tech.token(), "{\"notes\":\"Done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationSeconds").value(1800))
                .andExpect(jsonPath("$.notes").value("Done"))
                // Local task: nothing to push anywhere.
                .andExpect(jsonPath("$.syncStatus").value("NOT_REQUIRED"));

        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TRAVEL\"}"))
                .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(10));

        mvc.perform(auth(post("/api/work/day/end"), tech.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // Ending the day stopped the travel timer too.
        mvc.perform(auth(get("/api/work/current"), tech.token()))
                .andExpect(jsonPath("$.workDayOpen").value(false));
        mvc.perform(auth(post("/api/work/timers/stop"), tech.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_RUNNING_TIMER"));
    }

    @Test
    void businessRulesReturnClearErrors() throws Exception {
        Tech tech = newTech();
        Tech other = newTech();
        Task othersTask = createTask(null, other.user());

        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TRAVEL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_OPEN_WORKDAY"));

        mvc.perform(auth(post("/api/work/day/start"), tech.token())).andExpect(status().isOk());
        mvc.perform(auth(post("/api/work/day/start"), tech.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WORKDAY_ALREADY_OPEN"));

        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TASK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_REQUIRED"));

        mvc.perform(json(post("/api/work/timers/start"), tech.token(),
                        "{\"type\":\"TASK\",\"taskId\":" + othersTask.getId() + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_NOT_ASSIGNED"));

        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TRAVEL\"}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TRAVEL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TIMER_ALREADY_RUNNING"));

        mvc.perform(json(post("/api/work/location"), tech.token(), "{\"latitude\":200,\"longitude\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentClockInsCreateOnlyOneWorkDay() throws Exception {
        Tech tech = newTech();
        int requests = 6;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < requests; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mvc.perform(auth(post("/api/work/day/start"), tech.token())).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(200, 409);
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(workDays.findAll()).filteredOn(d -> d.getUser().getId().equals(tech.user().getId()))
                .filteredOn(d -> d.getStatus() == WorkDay.Status.OPEN)
                .hasSize(1);
    }

    @Test
    void leavingTheSiteWithARunningTimerNotifiesOnce() throws Exception {
        Tech tech = newTech();
        Site site = createSite(19.4326, -99.1332, 100);
        Task task = createTask(site, tech.user());
        mvc.perform(auth(post("/api/work/day/start"), tech.token())).andExpect(status().isOk());
        mvc.perform(json(post("/api/work/timers/start"), tech.token(),
                "{\"type\":\"TASK\",\"taskId\":" + task.getId() + "}")).andExpect(status().isOk());

        // ~40 m away: inside radius + tolerance.
        mvc.perform(json(post("/api/work/location"), tech.token(), "{\"latitude\":19.4329,\"longitude\":-99.1335}"))
                .andExpect(jsonPath("$.insideSite").value(true))
                .andExpect(jsonPath("$.notified").value(false));

        // ~1.1 km away.
        mvc.perform(json(post("/api/work/location"), tech.token(), "{\"latitude\":19.4426,\"longitude\":-99.1332}"))
                .andExpect(jsonPath("$.insideSite").value(false))
                .andExpect(jsonPath("$.notified").value(true));
        assertThat(pushes.sentTo(tech.deviceToken())).singleElement().satisfies(p -> {
            assertThat(p.data()).containsEntry("type", "LEFT_SITE");
            assertThat(p.body()).contains("Install router");
        });

        // Still away a few minutes later: no spam.
        clock.advance(Duration.ofMinutes(5));
        mvc.perform(json(post("/api/work/location"), tech.token(), "{\"latitude\":19.4426,\"longitude\":-99.1332}"))
                .andExpect(jsonPath("$.notified").value(false));

        // After the repeat interval it reminds again.
        clock.advance(Duration.ofMinutes(15));
        mvc.perform(json(post("/api/work/location"), tech.token(), "{\"latitude\":19.4426,\"longitude\":-99.1332}"))
                .andExpect(jsonPath("$.notified").value(true));
        assertThat(pushes.sentTo(tech.deviceToken())).hasSize(2);

        mvc.perform(json(post("/api/work/timers/stop"), tech.token(), "{}"))
                .andExpect(jsonPath("$.leftSiteAt").isNotEmpty());
    }

    @Test
    void remindsAboutTimersAndDaysLeftOpen() throws Exception {
        Tech tech = newTech();
        mvc.perform(auth(post("/api/work/day/start"), tech.token())).andExpect(status().isOk());
        mvc.perform(json(post("/api/work/timers/start"), tech.token(), "{\"type\":\"TRAVEL\"}"))
                .andExpect(status().isOk());

        clock.advance(Duration.ofHours(3));
        reminders.scan();
        assertThat(pushes.sentTo(tech.deviceToken())).isEmpty();

        clock.advance(Duration.ofHours(2));
        reminders.scan();
        assertThat(pushes.sentTo(tech.deviceToken())).extracting(p -> p.data().get("type")).containsExactly("TIMER_OPEN");

        reminders.scan();
        assertThat(pushes.sentTo(tech.deviceToken())).hasSize(1);

        clock.advance(Duration.ofHours(8));
        reminders.scan();
        assertThat(pushes.sentTo(tech.deviceToken())).extracting(p -> p.data().get("type"))
                .containsExactlyInAnyOrder("TIMER_OPEN", "TIMER_OPEN", "WORKDAY_OPEN");
    }

    @Test
    void adminCanManageUsersSitesAndTasks() throws Exception {
        String admin = newAdminToken();
        String body = mvc.perform(json(post("/api/admin/users"), admin,
                        "{\"email\":\"New.Tech@Example.com\",\"password\":\"long-enough-pw\",\"fullName\":\"New Tech\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.tech@example.com"))
                .andExpect(jsonPath("$.role").value(Role.TECHNICIAN.name()))
                .andReturn().getResponse().getContentAsString();
        int userId = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mvc.perform(json(post("/api/admin/users"), admin,
                        "{\"email\":\"new.tech@example.com\",\"password\":\"long-enough-pw\"}"))
                .andExpect(status().isConflict());

        String site = mvc.perform(json(post("/api/admin/sites"), admin,
                        "{\"name\":\"Warehouse\",\"geofence\":{\"latitude\":10,\"longitude\":20,\"radiusMeters\":80}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.radiusMeters").value(80))
                .andReturn().getResponse().getContentAsString();
        int siteId = com.jayway.jsonpath.JsonPath.read(site, "$.id");

        mvc.perform(json(post("/api/admin/tasks"), admin,
                        "{\"title\":\"Inventory\",\"siteId\":" + siteId + ",\"assigneeIds\":[" + userId + "]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.site.latitude").value(10.0));
    }
}
