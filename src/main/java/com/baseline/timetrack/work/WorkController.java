package com.baseline.timetrack.work;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.auth.CurrentUser;
import com.baseline.timetrack.work.WorkDtos.ClockRequest;
import com.baseline.timetrack.work.WorkDtos.CurrentStatusResponse;
import com.baseline.timetrack.work.WorkDtos.LocationInput;
import com.baseline.timetrack.work.WorkDtos.LocationReportResponse;
import com.baseline.timetrack.work.WorkDtos.StartTimerRequest;
import com.baseline.timetrack.work.WorkDtos.StopTimerRequest;
import com.baseline.timetrack.work.WorkDtos.TimerResponse;
import com.baseline.timetrack.work.WorkDtos.WorkDayResponse;

import jakarta.validation.Valid;

/** Technician endpoints. The user always comes from the bearer token. */
@RestController
@RequestMapping("/api/work")
public class WorkController {

    private final WorkService workService;
    private final LocationService locationService;

    public WorkController(WorkService workService, LocationService locationService) {
        this.workService = workService;
        this.locationService = locationService;
    }

    @PostMapping("/day/start")
    public WorkDayResponse startDay(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody(required = false) ClockRequest request) {
        return workService.startDay(CurrentUser.id(jwt), request == null ? null : LocationInput.geoPointOf(request.location()));
    }

    @PostMapping("/day/end")
    public WorkDayResponse endDay(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody(required = false) ClockRequest request) {
        return workService.endDay(CurrentUser.id(jwt), request == null ? null : LocationInput.geoPointOf(request.location()));
    }

    @PostMapping("/timers/start")
    public TimerResponse startTimer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody StartTimerRequest request) {
        return workService.startTimer(CurrentUser.id(jwt), request);
    }

    @PostMapping("/timers/stop")
    public TimerResponse stopTimer(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody(required = false) StopTimerRequest request) {
        return request == null
                ? workService.stopTimer(CurrentUser.id(jwt), null, null)
                : workService.stopTimer(CurrentUser.id(jwt), request.notes(), LocationInput.geoPointOf(request.location()));
    }

    @GetMapping("/current")
    public CurrentStatusResponse current(@AuthenticationPrincipal Jwt jwt) {
        return workService.current(CurrentUser.id(jwt));
    }

    @PostMapping("/location")
    public LocationReportResponse reportLocation(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody LocationInput location) {
        return locationService.report(CurrentUser.id(jwt), location);
    }
}
