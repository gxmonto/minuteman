package com.baseline.timetrack.task;

import java.util.EnumSet;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.auth.CurrentUser;
import com.baseline.timetrack.entity.TaskStatus;
import com.baseline.timetrack.integration.TaskSyncService;
import com.baseline.timetrack.repository.TaskRepository;
import com.baseline.timetrack.task.TaskDtos.TaskResponse;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskRepository tasks;
    private final TaskSyncService taskSync;

    public TaskController(TaskRepository tasks, TaskSyncService taskSync) {
        this.tasks = tasks;
        this.taskSync = taskSync;
    }

    /** The technician's open tasks, from every connected system plus local ones. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<TaskResponse> myTasks(@AuthenticationPrincipal Jwt jwt) {
        return tasks.findAssignedTo(CurrentUser.id(jwt), EnumSet.of(TaskStatus.OPEN, TaskStatus.IN_PROGRESS)).stream()
                .map(TaskResponse::of)
                .toList();
    }

    /** Pull-to-refresh: re-fetches this user's tasks from the connected systems first. */
    @PostMapping("/refresh")
    public List<TaskResponse> refresh(@AuthenticationPrincipal Jwt jwt) {
        taskSync.syncUser(CurrentUser.id(jwt));
        return myTasks(jwt);
    }
}
