-- HealthQueue core schema (MySQL 8)

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE departments (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_departments_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE doctors (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    department_id BIGINT      NOT NULL,
    title        VARCHAR(100) NOT NULL,          -- e.g. "General Physician"
    token_prefix VARCHAR(1)   NOT NULL,         -- letter shown in tokens, e.g. A-012
    slot_minutes INT          NOT NULL,          -- length of one bookable slot
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    working_days VARCHAR(40)  NOT NULL,          -- CSV of MON,TUE,...
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uq_doctors_user UNIQUE (user_id),
    CONSTRAINT fk_doctors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_doctors_department FOREIGN KEY (department_id) REFERENCES departments (id),
    INDEX idx_doctors_department (department_id)
) ENGINE = InnoDB;

-- One row per doctor per day. It holds the token counter and is the row we lock
-- (SELECT ... FOR UPDATE) to serialise bookings and queue transitions.
CREATE TABLE queue_states (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    doctor_id         BIGINT NOT NULL,
    queue_date        DATE   NOT NULL,
    last_token_number INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_queue_doctor_date UNIQUE (doctor_id, queue_date),
    CONSTRAINT fk_queue_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id)
) ENGINE = InnoDB;

CREATE TABLE appointments (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    patient_id       BIGINT      NOT NULL,
    doctor_id        BIGINT      NOT NULL,
    appointment_date DATE        NOT NULL,
    slot_time        TIME        NOT NULL,
    token_number     INT         NOT NULL,
    status           VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    called_at        DATETIME(6) NULL,
    completed_at     DATETIME(6) NULL,
    -- NULL when cancelled so that a cancelled slot can be re-booked while
    -- uniqueness is still enforced for every live appointment.
    active_flag      TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'CANCELLED' THEN NULL ELSE 1 END) VIRTUAL,
    PRIMARY KEY (id),
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id) REFERENCES users (id),
    CONSTRAINT fk_appt_doctor FOREIGN KEY (doctor_id) REFERENCES doctors (id),
    -- A token is never reused in a doctor/date queue (even after cancellation).
    CONSTRAINT uq_appt_token UNIQUE (doctor_id, appointment_date, token_number),
    -- A slot can hold only one live appointment.
    CONSTRAINT uq_appt_slot UNIQUE (doctor_id, appointment_date, slot_time, active_flag),
    -- A patient has at most one live appointment per doctor per day.
    CONSTRAINT uq_appt_patient_day UNIQUE (patient_id, doctor_id, appointment_date, active_flag),
    INDEX idx_appt_queue (doctor_id, appointment_date, status, token_number),
    INDEX idx_appt_patient (patient_id, appointment_date)
) ENGINE = InnoDB;
