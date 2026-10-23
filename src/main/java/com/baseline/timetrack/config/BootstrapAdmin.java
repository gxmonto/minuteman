package com.baseline.timetrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baseline.timetrack.entity.Role;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.repository.UserRepository;

@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.BootstrapAdmin props;

    public BootstrapAdmin(UserRepository users, PasswordEncoder passwordEncoder, AppProperties props) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.props = props.bootstrapAdmin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0 || !StringUtils.hasText(props.email()) || !StringUtils.hasText(props.password())) {
            return;
        }
        User admin = new User();
        admin.setEmail(props.email().trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(props.password()));
        admin.setFullName("Administrator");
        admin.setRole(Role.ADMIN);
        users.save(admin);
        log.info("Created bootstrap admin {}", admin.getEmail());
    }
}
