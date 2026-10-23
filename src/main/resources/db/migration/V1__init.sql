-- Written to run on MySQL 8 and on H2 in MySQL mode (used by the tests).

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    role          VARCHAR(32)  NOT NULL,
    active        BOOLEAN      NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE external_accounts (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(64)  NOT NULL,
    external_user_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_external_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_external_accounts_provider_external UNIQUE (provider, external_user_id),
    CONSTRAINT uk_external_accounts_user_provider UNIQUE (user_id, provider)
);

CREATE TABLE sites (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    source           VARCHAR(64)  NOT NULL,
    external_id      VARCHAR(255),
    name             VARCHAR(255) NOT NULL,
    address          VARCHAR(500),
    latitude         DOUBLE,
    longitude        DOUBLE,
    radius_meters    INT,
    location_locked  BOOLEAN      NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_sites_source_external UNIQUE (source, external_id)
);

CREATE TABLE tasks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    source      VARCHAR(64)  NOT NULL,
    external_id VARCHAR(255),
    title       VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(32)  NOT NULL,
    site_id     BIGINT,
    due_at      DATETIME(6),
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT fk_tasks_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT uk_tasks_source_external UNIQUE (source, external_id)
);

CREATE TABLE task_assignees (
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, user_id),
    CONSTRAINT fk_task_assignees_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_task_assignees_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE work_days (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    clock_in            DATETIME(6) NOT NULL,
    clock_out           DATETIME(6),
    status              VARCHAR(16) NOT NULL,
    open_flag           BOOLEAN,
    clock_in_latitude   DOUBLE,
    clock_in_longitude  DOUBLE,
    clock_out_latitude  DOUBLE,
    clock_out_longitude DOUBLE,
    last_reminder_at    DATETIME(6),
    CONSTRAINT fk_work_days_user FOREIGN KEY (user_id) REFERENCES users (id),
    -- open_flag is TRUE while open and NULL when closed: one open day per user, enforced by the DB.
    CONSTRAINT uk_work_days_one_open UNIQUE (user_id, open_flag)
);

CREATE TABLE timer_entries (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_day_id           BIGINT      NOT NULL,
    user_id               BIGINT      NOT NULL,
    task_id               BIGINT,
    site_id               BIGINT,
    type                  VARCHAR(16) NOT NULL,
    start_time            DATETIME(6) NOT NULL,
    end_time              DATETIME(6),
    duration_seconds      BIGINT,
    notes                 VARCHAR(2000),
    open_flag             BOOLEAN,
    start_latitude        DOUBLE,
    start_longitude       DOUBLE,
    end_latitude          DOUBLE,
    end_longitude         DOUBLE,
    left_site_at          DATETIME(6),
    left_site_notified_at DATETIME(6),
    last_reminder_at      DATETIME(6),
    sync_status           VARCHAR(16) NOT NULL,
    sync_attempts         INT         NOT NULL,
    next_sync_at          DATETIME(6),
    last_sync_error       VARCHAR(1000),
    external_id           VARCHAR(255),
    updated_at            DATETIME(6) NOT NULL,
    CONSTRAINT fk_timer_entries_work_day FOREIGN KEY (work_day_id) REFERENCES work_days (id),
    CONSTRAINT fk_timer_entries_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_timer_entries_task FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_timer_entries_site FOREIGN KEY (site_id) REFERENCES sites (id),
    CONSTRAINT uk_timer_entries_one_open UNIQUE (user_id, open_flag)
);

CREATE INDEX ix_timer_entries_work_day ON timer_entries (work_day_id);
CREATE INDEX ix_timer_entries_sync ON timer_entries (sync_status, next_sync_at);
CREATE INDEX ix_timer_entries_updated ON timer_entries (updated_at, id);

CREATE TABLE device_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    platform   VARCHAR(16),
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_device_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
);
