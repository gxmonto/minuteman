package com.baseline.timetrack.notification;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.auth.CurrentUser;
import com.baseline.timetrack.entity.DeviceToken;
import com.baseline.timetrack.repository.DeviceTokenRepository;
import com.baseline.timetrack.repository.UserRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The app registers its FCM token after login and removes it on logout. */
@RestController
@RequestMapping("/api/me/devices")
public class DeviceController {

    private final DeviceTokenRepository deviceTokens;
    private final UserRepository users;
    private final Clock clock;

    public DeviceController(DeviceTokenRepository deviceTokens, UserRepository users, Clock clock) {
        this.deviceTokens = deviceTokens;
        this.users = users;
        this.clock = clock;
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 512) String token,
            @Pattern(regexp = "android|ios|web") String platform) {
    }

    public record DeviceDeleteRequest(@NotBlank String token) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceRequest request) {
        // A token identifies a device; if someone else logs in on it, it moves to them.
        DeviceToken device = deviceTokens.findByToken(request.token()).orElseGet(DeviceToken::new);
        device.setToken(request.token());
        device.setUser(users.getReferenceById(CurrentUser.id(jwt)));
        device.setPlatform(request.platform());
        device.setUpdatedAt(clock.instant());
        deviceTokens.save(device);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unregister(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeviceDeleteRequest request) {
        deviceTokens.deleteByUserIdAndToken(CurrentUser.id(jwt), request.token());
    }
}
