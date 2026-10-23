package com.baseline.timetrack.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.Role;
import com.baseline.timetrack.entity.Site;
import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.repository.SiteRepository;
import com.baseline.timetrack.repository.TaskRepository;
import com.baseline.timetrack.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class ApiTestBase {

    protected static final String PASSWORD = "correct-horse-battery";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected UserRepository users;
    @Autowired
    protected TaskRepository tasks;
    @Autowired
    protected SiteRepository sites;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected RecordingPushSender pushes;

    protected record Tech(User user, String token, String deviceToken) {
    }

    protected User createUser(Role role) {
        User user = new User();
        user.setEmail("user-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Test " + role);
        user.setRole(role);
        return users.save(user);
    }

    protected String login(User user) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /** A logged-in technician with a registered push device. */
    protected Tech newTech() throws Exception {
        User user = createUser(Role.TECHNICIAN);
        String token = login(user);
        String device = "device-" + UUID.randomUUID();
        mvc.perform(auth(post("/api/me/devices"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + device + "\",\"platform\":\"android\"}"))
                .andExpect(status().isNoContent());
        return new Tech(user, token, device);
    }

    protected String newAdminToken() throws Exception {
        return login(createUser(Role.ADMIN));
    }

    protected Site createSite(double lat, double lng, int radius) {
        Site site = new Site();
        site.setSource("local");
        site.setName("Site " + UUID.randomUUID());
        site.setLocation(GeoPoint.of(lat, lng));
        site.setRadiusMeters(radius);
        return sites.save(site);
    }

    protected Task createTask(Site site, User... assignees) {
        Task task = new Task();
        task.setSource("local");
        task.setTitle("Install router");
        task.setSite(site);
        task.setAssignees(new java.util.HashSet<>(Set.of(assignees)));
        return tasks.save(task);
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String token, String body) {
        return auth(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
