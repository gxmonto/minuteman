package com.baseline.timetrack.admin;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.common.ApiException;
import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.ExternalAccount;
import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.Role;
import com.baseline.timetrack.entity.Site;
import com.baseline.timetrack.entity.SyncStatus;
import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.integration.ConnectorRegistry;
import com.baseline.timetrack.integration.IntegrationConnector;
import com.baseline.timetrack.integration.TaskSyncService;
import com.baseline.timetrack.integration.TimeEntrySyncService;
import com.baseline.timetrack.repository.ExternalAccountRepository;
import com.baseline.timetrack.repository.SiteRepository;
import com.baseline.timetrack.repository.TaskRepository;
import com.baseline.timetrack.repository.TimerEntryRepository;
import com.baseline.timetrack.repository.UserRepository;
import com.baseline.timetrack.task.TaskDtos.SiteResponse;
import com.baseline.timetrack.task.TaskDtos.TaskResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Back-office endpoints; ROLE_ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository users;
    private final ExternalAccountRepository externalAccounts;
    private final SiteRepository sites;
    private final TaskRepository tasks;
    private final TimerEntryRepository timers;
    private final PasswordEncoder passwordEncoder;
    private final ConnectorRegistry registry;
    private final TaskSyncService taskSync;
    private final TimeEntrySyncService timeEntrySync;
    private final AppProperties props;

    public AdminController(UserRepository users, ExternalAccountRepository externalAccounts, SiteRepository sites,
                           TaskRepository tasks, TimerEntryRepository timers, PasswordEncoder passwordEncoder,
                           ConnectorRegistry registry, TaskSyncService taskSync, TimeEntrySyncService timeEntrySync,
                           AppProperties props) {
        this.users = users;
        this.externalAccounts = externalAccounts;
        this.sites = sites;
        this.tasks = tasks;
        this.timers = timers;
        this.passwordEncoder = passwordEncoder;
        this.registry = registry;
        this.taskSync = taskSync;
        this.timeEntrySync = timeEntrySync;
        this.props = props;
    }

    // ===== users =====

    public record LinkedAccount(String provider, String externalUserId) {
    }

    public record AdminUserResponse(Long id, String email, String fullName, Role role, boolean active,
                                    List<LinkedAccount> linkedAccounts) {
    }

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 255) String fullName,
            Role role) {
    }

    public record UpdateUserRequest(
            @Size(max = 255) String fullName,
            Role role,
            Boolean active,
            @Size(min = 8, max = 128) String password) {
    }

    public record LinkAccountRequest(@NotBlank @Size(max = 255) String externalUserId) {
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    public List<AdminUserResponse> listUsers() {
        return users.findAll().stream().map(this::toResponse).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AdminUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "A user with this email already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(request.role() == null ? Role.TECHNICIAN : request.role());
        return toResponse(users.save(user));
    }

    @PatchMapping("/users/{id}")
    @Transactional
    public AdminUserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        User user = requireUser(id);
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(user);
    }

    /** Maps the user to their id in an external system, e.g. their Bitrix24 user id. */
    @PutMapping("/users/{id}/external-accounts/{provider}")
    @Transactional
    public AdminUserResponse linkAccount(@PathVariable Long id, @PathVariable String provider,
                                         @Valid @RequestBody LinkAccountRequest request) {
        User user = requireUser(id);
        try {
            ConnectorRegistry.validateKey(provider);
        } catch (IllegalStateException e) {
            throw ApiException.badRequest("INVALID_PROVIDER", e.getMessage());
        }
        externalAccounts.findByProviderAndExternalUserId(provider, request.externalUserId())
                .filter(a -> !a.getUser().getId().equals(id))
                .ifPresent(a -> {
                    throw ApiException.conflict("EXTERNAL_ACCOUNT_TAKEN", "That external user is linked to someone else");
                });
        ExternalAccount account = externalAccounts.findByUserIdAndProvider(id, provider).orElseGet(() -> {
            ExternalAccount a = new ExternalAccount();
            a.setUser(user);
            a.setProvider(provider);
            return a;
        });
        account.setExternalUserId(request.externalUserId());
        externalAccounts.save(account);
        return toResponse(user);
    }

    @DeleteMapping("/users/{id}/external-accounts/{provider}")
    @Transactional
    public AdminUserResponse unlinkAccount(@PathVariable Long id, @PathVariable String provider) {
        User user = requireUser(id);
        externalAccounts.findByUserIdAndProvider(id, provider).ifPresent(externalAccounts::delete);
        externalAccounts.flush();
        return toResponse(user);
    }

    private AdminUserResponse toResponse(User u) {
        List<LinkedAccount> linked = externalAccounts.findByUserId(u.getId()).stream()
                .map(a -> new LinkedAccount(a.getProvider(), a.getExternalUserId()))
                .toList();
        return new AdminUserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isActive(), linked);
    }

    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    // ===== sites & geofences =====

    public record GeofenceRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Min(10) @Max(10_000) Integer radiusMeters) {
    }

    public record CreateSiteRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String address,
            @Valid GeofenceRequest geofence) {
    }

    @GetMapping("/sites")
    @Transactional(readOnly = true)
    public List<SiteResponse> listSites() {
        return sites.findAll().stream().map(SiteResponse::of).toList();
    }

    @PostMapping("/sites")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SiteResponse createSite(@Valid @RequestBody CreateSiteRequest request) {
        Site site = new Site();
        site.setSource(ConnectorRegistry.LOCAL);
        site.setName(request.name());
        site.setAddress(request.address());
        site.setRadiusMeters(props.sites().defaultRadiusMeters());
        if (request.geofence() != null) {
            applyGeofence(site, request.geofence());
        }
        return SiteResponse.of(sites.save(site));
    }

    /** Places a site's geofence by hand; later syncs from its provider will not move it. */
    @PutMapping("/sites/{id}/geofence")
    @Transactional
    public SiteResponse setGeofence(@PathVariable Long id, @Valid @RequestBody GeofenceRequest request) {
        Site site = sites.findById(id).orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "Site not found"));
        applyGeofence(site, request);
        return SiteResponse.of(site);
    }

    private void applyGeofence(Site site, GeofenceRequest geofence) {
        site.setLocation(GeoPoint.of(geofence.latitude(), geofence.longitude()));
        if (geofence.radiusMeters() != null) {
            site.setRadiusMeters(geofence.radiusMeters());
        }
        site.setLocationLocked(true);
    }

    // ===== tasks =====

    public record CreateTaskRequest(
            @NotBlank @Size(max = 255) String title,
            String description,
            Long siteId,
            Set<Long> assigneeIds,
            Instant dueAt) {
    }

    public record SetTaskSiteRequest(Long siteId) {
    }

    /** Tasks created directly in Minuteman, for teams or jobs without an external system. */
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public TaskResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task task = new Task();
        task.setSource(ConnectorRegistry.LOCAL);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueAt(request.dueAt());
        task.setSite(request.siteId() == null ? null : requireSite(request.siteId()));
        if (request.assigneeIds() != null) {
            List<User> assignees = users.findAllById(request.assigneeIds());
            if (assignees.size() != request.assigneeIds().size()) {
                throw ApiException.badRequest("UNKNOWN_ASSIGNEE", "Some assignee ids do not exist");
            }
            task.setAssignees(new HashSet<>(assignees));
        }
        return TaskResponse.of(tasks.save(task));
    }

    /** Assigns a site to any task, e.g. Bitrix tasks whose site field is empty. Syncs keep it. */
    @PutMapping("/tasks/{id}/site")
    @Transactional
    public TaskResponse setTaskSite(@PathVariable Long id, @RequestBody SetTaskSiteRequest request) {
        Task task = tasks.findById(id).orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Task not found"));
        task.setSite(request.siteId() == null ? null : requireSite(request.siteId()));
        return TaskResponse.of(task);
    }

    private Site requireSite(Long id) {
        return sites.findById(id).orElseThrow(() -> ApiException.notFound("SITE_NOT_FOUND", "Site not found"));
    }

    // ===== integrations =====

    public record ConnectorInfo(String provider, boolean pushesTimeEntries) {
    }

    public record IntegrationsResponse(List<ConnectorInfo> connectors, List<String> inboundProviders) {
    }

    public record SyncFailure(Long timerId, Long userId, String userEmail, Long taskId, String provider,
                              int attempts, String lastError, Instant endTime) {
    }

    @GetMapping("/integrations")
    public IntegrationsResponse integrations() {
        List<ConnectorInfo> connectors = registry.all().stream()
                .map((IntegrationConnector c) -> new ConnectorInfo(c.provider(), c.supportsTimeEntryPush()))
                .toList();
        return new IntegrationsResponse(connectors,
                props.integrations().inboundApiKeys().keySet().stream().sorted().toList());
    }

    @PostMapping("/integrations/{provider}/sync")
    public TaskSyncService.SyncResult syncProvider(@PathVariable String provider) {
        return taskSync.syncProvider(provider);
    }

    @GetMapping("/time-entries/sync-failures")
    @Transactional(readOnly = true)
    public List<SyncFailure> syncFailures() {
        return timers.findBySyncStatusOrderByIdDesc(SyncStatus.FAILED, PageRequest.of(0, 200)).stream()
                .map(t -> new SyncFailure(t.getId(), t.getUser().getId(), t.getUser().getEmail(), t.getTask().getId(),
                        t.getTask().getSource(), t.getSyncAttempts(), t.getLastSyncError(), t.getEndTime()))
                .toList();
    }

    @PostMapping("/time-entries/{id}/retry-sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void retrySync(@PathVariable Long id) {
        timeEntrySync.retry(id);
    }
}
