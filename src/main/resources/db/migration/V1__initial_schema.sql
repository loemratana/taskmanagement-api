-- Baseline schema generated from the JPA entities (Hibernate ddl-auto=create + pg_dump --schema-only).
-- Later migrations (V2+) build on this.


CREATE TABLE approvals (
    due_date date,
    status smallint,
    created_at timestamp(6) with time zone NOT NULL,
    processed_at timestamp(6) without time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    approver_id uuid NOT NULL,
    entity_id uuid NOT NULL,
    id uuid NOT NULL,
    processed_by uuid,
    requester_id uuid NOT NULL,
    entity_type character varying(50) NOT NULL,
    request_type character varying(50) NOT NULL,
    approver_comment character varying(255),
    data jsonb,
    description character varying(255),
    title character varying(255) NOT NULL,
    CONSTRAINT approvals_status_check CHECK (((status >= 0) AND (status <= 3)))
);

CREATE TABLE attachments (
    created_at timestamp(6) with time zone NOT NULL,
    file_size bigint NOT NULL,
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    user_id uuid NOT NULL,
    file_type character varying(100) NOT NULL,
    file_name character varying(255) NOT NULL,
    file_url character varying(255) NOT NULL,
    original_name character varying(255) NOT NULL
);

CREATE TABLE department_telegram_groups (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    verified_at timestamp(6) with time zone,
    department_id uuid NOT NULL,
    id uuid NOT NULL,
    chat_id character varying(64) NOT NULL,
    group_name character varying(255)
);

CREATE TABLE departments (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    manager_id uuid,
    description text,
    name character varying(255) NOT NULL
);

CREATE TABLE notification_deliveries (
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone,
    last_attempt_at timestamp(6) with time zone,
    processed_at timestamp(6) with time zone,
    scheduled_at timestamp(6) with time zone,
    sent_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    notification_id uuid NOT NULL,
    channel character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    error_message text,
    CONSTRAINT notification_deliveries_channel_check CHECK (((channel)::text = ANY ((ARRAY['IN_APP'::character varying, 'EMAIL'::character varying, 'TELEGRAM'::character varying, 'TELEGRAM_GROUP'::character varying, 'PUSH'::character varying])::text[]))),
    CONSTRAINT notification_deliveries_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'DEAD_LETTER'::character varying])::text[])))
);

CREATE TABLE notifications (
    is_read boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    read_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    recipient_id uuid NOT NULL,
    reference_id uuid,
    sender_id uuid NOT NULL,
    message text NOT NULL,
    priority character varying(255) NOT NULL,
    reference_type character varying(255),
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    metadata jsonb,
    CONSTRAINT notifications_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'URGENT'::character varying, 'NORMAL'::character varying])::text[]))),
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PARTIAL'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'SENT'::character varying])::text[]))),
    CONSTRAINT notifications_type_check CHECK (((type)::text = ANY ((ARRAY['TASK_ASSIGNED'::character varying, 'TASK_UPDATED'::character varying, 'TASK_COMPLETED'::character varying, 'TASK_OVERDUE'::character varying, 'TASK_COMMENTED'::character varying, 'TASK_REMINDER'::character varying, 'APPROVAL_REQUEST'::character varying, 'APPROVAL_APPROVED'::character varying, 'APPROVAL_REJECTED'::character varying, 'USER_MENTIONED'::character varying, 'SYSTEM_ANNOUNCEMENT'::character varying, 'SYSTEM_MAINTENANCE'::character varying])::text[])))
);

CREATE TABLE permissions (
    is_active boolean,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    action character varying(50) NOT NULL,
    module character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    description text
);

CREATE TABLE positions (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    name character varying(150) NOT NULL,
    description text
);

CREATE TABLE refresh_token (
    created_at timestamp(6) with time zone,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    ip_address inet,
    device_type character varying(30),
    device_name character varying(100),
    token_hash text NOT NULL,
    user_agent text
);

CREATE TABLE role_permissions (
    permission_id uuid NOT NULL,
    role_id uuid NOT NULL
);

CREATE TABLE roles (
    is_active boolean,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    name character varying(100) NOT NULL,
    description text
);

CREATE TABLE task_assignments (
    assigned_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    assigned_by_id uuid,
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status character varying(255),
    CONSTRAINT task_assignments_status_check CHECK (((status)::text = ANY ((ARRAY['ASSIGNED'::character varying, 'ACCEPTED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying])::text[])))
);

CREATE TABLE task_comments (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    task_id uuid,
    user_id uuid,
    content text NOT NULL
);

CREATE TABLE task_logs (
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    task_id uuid,
    user_id uuid,
    ip_address character varying(45),
    action_type character varying(50) NOT NULL,
    description character varying(255),
    new_value jsonb,
    old_value jsonb
);

CREATE TABLE task_reminder_logs (
    reminder_date date NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    sent_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    notification_id uuid,
    task_id uuid NOT NULL,
    task_reminder_schedule_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    CONSTRAINT task_reminder_logs_status_check CHECK (((status)::text = ANY ((ARRAY['SENT'::character varying, 'SKIPPED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE task_reminder_schedules (
    active boolean NOT NULL,
    reminder_time time(0) without time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    task_reminder_setting_id uuid NOT NULL
);

CREATE TABLE task_reminder_settings (
    enabled boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    timezone character varying(64) NOT NULL
);

CREATE TABLE tasks (
    actual_hours numeric(6,2),
    due_date date,
    estimated_hours numeric(6,2),
    is_active boolean,
    progress integer,
    start_date date,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    assigned_by_id uuid,
    assignee_id uuid,
    creator_id uuid,
    department_id uuid NOT NULL,
    id uuid NOT NULL,
    parent_task_id uuid,
    priority character varying(20),
    status character varying(50),
    description text,
    title text NOT NULL,
    tags jsonb,
    CONSTRAINT tasks_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'URGENT'::character varying])::text[]))),
    CONSTRAINT tasks_status_check CHECK (((status)::text = ANY ((ARRAY['TODO'::character varying, 'IN_PROGRESS'::character varying, 'IN_REVIEW'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'DONE'::character varying, 'BLOCKED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE user_devices (
    active boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_active_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    device_token character varying(512) NOT NULL,
    device_type character varying(255) NOT NULL,
    device_info jsonb,
    CONSTRAINT user_devices_device_type_check CHECK (((device_type)::text = ANY ((ARRAY['ANDROID'::character varying, 'IOS'::character varying, 'WEB'::character varying, 'UNKNOWN'::character varying])::text[])))
);

CREATE TABLE user_notification_channels (
    enabled boolean NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    channel character varying(255) NOT NULL,
    config jsonb,
    CONSTRAINT user_notification_channels_channel_check CHECK (((channel)::text = ANY ((ARRAY['IN_APP'::character varying, 'EMAIL'::character varying, 'TELEGRAM'::character varying, 'TELEGRAM_GROUP'::character varying, 'PUSH'::character varying])::text[])))
);

CREATE TABLE user_roles (
    role_id uuid NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE users (
    is_active boolean,
    created_at timestamp(6) with time zone NOT NULL,
    last_login_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone NOT NULL,
    department_id uuid,
    id uuid NOT NULL,
    manager_id uuid,
    position_id uuid,
    status character varying(20),
    full_name character varying(200) NOT NULL,
    avatar_url text,
    email character varying(255) NOT NULL,
    password_hash text,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);

ALTER TABLE ONLY approvals
    ADD CONSTRAINT approvals_pkey PRIMARY KEY (id);

ALTER TABLE ONLY attachments
    ADD CONSTRAINT attachments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY department_telegram_groups
    ADD CONSTRAINT department_telegram_groups_department_id_key UNIQUE (department_id);

ALTER TABLE ONLY department_telegram_groups
    ADD CONSTRAINT department_telegram_groups_pkey PRIMARY KEY (id);

ALTER TABLE ONLY departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY notification_deliveries
    ADD CONSTRAINT notification_deliveries_pkey PRIMARY KEY (id);

ALTER TABLE ONLY notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_name_key UNIQUE (name);

ALTER TABLE ONLY permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY positions
    ADD CONSTRAINT positions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY refresh_token
    ADD CONSTRAINT refresh_token_pkey PRIMARY KEY (id);

ALTER TABLE ONLY refresh_token
    ADD CONSTRAINT refresh_token_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (permission_id, role_id);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);

ALTER TABLE ONLY roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_assignments
    ADD CONSTRAINT task_assignments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_assignments
    ADD CONSTRAINT task_assignments_task_id_user_id_key UNIQUE (task_id, user_id);

ALTER TABLE ONLY task_comments
    ADD CONSTRAINT task_comments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_logs
    ADD CONSTRAINT task_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_reminder_logs
    ADD CONSTRAINT task_reminder_logs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_reminder_logs
    ADD CONSTRAINT task_reminder_logs_task_id_task_reminder_schedule_id_remind_key UNIQUE (task_id, task_reminder_schedule_id, reminder_date);

ALTER TABLE ONLY task_reminder_schedules
    ADD CONSTRAINT task_reminder_schedules_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_reminder_schedules
    ADD CONSTRAINT task_reminder_schedules_task_reminder_setting_id_reminder_t_key UNIQUE (task_reminder_setting_id, reminder_time);

ALTER TABLE ONLY task_reminder_settings
    ADD CONSTRAINT task_reminder_settings_pkey PRIMARY KEY (id);

ALTER TABLE ONLY task_reminder_settings
    ADD CONSTRAINT task_reminder_settings_task_id_key UNIQUE (task_id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);

ALTER TABLE ONLY user_devices
    ADD CONSTRAINT user_devices_device_token_key UNIQUE (device_token);

ALTER TABLE ONLY user_devices
    ADD CONSTRAINT user_devices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY user_notification_channels
    ADD CONSTRAINT user_notification_channels_pkey PRIMARY KEY (id);

ALTER TABLE ONLY user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (role_id, user_id);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_deliveries_attempts ON notification_deliveries USING btree (attempts);

CREATE INDEX idx_deliveries_channel ON notification_deliveries USING btree (channel);

CREATE INDEX idx_deliveries_notification_id ON notification_deliveries USING btree (notification_id);

CREATE INDEX idx_deliveries_status ON notification_deliveries USING btree (status);

CREATE INDEX idx_role_name ON roles USING btree (name);

ALTER TABLE ONLY approvals
    ADD CONSTRAINT fk26bpb2a3url91ua6wpolo17v0 FOREIGN KEY (requester_id) REFERENCES users(id);

ALTER TABLE ONLY users
    ADD CONSTRAINT fk5p1ci5btqfwvtaqx5n2wxi182 FOREIGN KEY (manager_id) REFERENCES users(id);

ALTER TABLE ONLY task_comments
    ADD CONSTRAINT fk6n4f8xnvwdkbjci078pqdn1w1 FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY users
    ADD CONSTRAINT fk6ph6xiiydudp6umjf2xckbbmi FOREIGN KEY (position_id) REFERENCES positions(id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fk76tiq4q248au3u79a8nkexoth FOREIGN KEY (parent_task_id) REFERENCES tasks(id);

ALTER TABLE ONLY positions
    ADD CONSTRAINT fk7lkgjivwf8cd9o6s4h0ule242 FOREIGN KEY (department_id) REFERENCES departments(id);

ALTER TABLE ONLY notification_deliveries
    ADD CONSTRAINT fk7wuwttjtl6k3gh24n8erl562y FOREIGN KEY (notification_id) REFERENCES notifications(id);

ALTER TABLE ONLY task_comments
    ADD CONSTRAINT fk9517viwn2geh1gpivj6l9y64u FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE ONLY task_logs
    ADD CONSTRAINT fkci2dbmadju1pvodqi5nsmh5qb FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES permissions(id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fkekr1dgiqktpyoip3qmp6lxsit FOREIGN KEY (assignee_id) REFERENCES users(id);

ALTER TABLE ONLY task_reminder_schedules
    ADD CONSTRAINT fkf9nyn2wp86fjd64b8u6a552u0 FOREIGN KEY (task_reminder_setting_id) REFERENCES task_reminder_settings(id);

ALTER TABLE ONLY user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES roles(id);

ALTER TABLE ONLY user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY departments
    ADD CONSTRAINT fkia71ute4clfbt6u2auufvw1bv FOREIGN KEY (manager_id) REFERENCES users(id);

ALTER TABLE ONLY task_reminder_settings
    ADD CONSTRAINT fkj67e5c9uevor0spbojeabd2dn FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE ONLY refresh_token
    ADD CONSTRAINT fkjtx87i0jvq2svedphegvdwcuy FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fkk223sqirb7d32pd0rbgips492 FOREIGN KEY (assigned_by_id) REFERENCES users(id);

ALTER TABLE ONLY task_assignments
    ADD CONSTRAINT fkk36vhf9tt6t3woselwnkis6v6 FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE ONLY department_telegram_groups
    ADD CONSTRAINT fkkccdr6d01ggk2e4x1cp4dgwr0 FOREIGN KEY (department_id) REFERENCES departments(id);

ALTER TABLE ONLY approvals
    ADD CONSTRAINT fkkh8kt9y5pin7d4qxs8i7pak8d FOREIGN KEY (approver_id) REFERENCES users(id);

ALTER TABLE ONLY task_reminder_logs
    ADD CONSTRAINT fklgmr0vhtp07b5ikdcr91vs50v FOREIGN KEY (task_reminder_schedule_id) REFERENCES task_reminder_schedules(id);

ALTER TABLE ONLY role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES roles(id);

ALTER TABLE ONLY task_reminder_logs
    ADD CONSTRAINT fko2oe5yory0navot94puakjt6k FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE ONLY task_assignments
    ADD CONSTRAINT fkovnod7lqp56uups16si7jh6uu FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY approvals
    ADD CONSTRAINT fkpasmjjsgt2n2s3ss2lqik7aga FOREIGN KEY (processed_by) REFERENCES users(id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fkq0ulb2lfkghkpoakoyd4es4jp FOREIGN KEY (department_id) REFERENCES departments(id);

ALTER TABLE ONLY attachments
    ADD CONSTRAINT fkq4u9ne3x0xtpc5d2jdddv1ii7 FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE ONLY attachments
    ADD CONSTRAINT fkrap79tymgdjf1c5x4dla3rekl FOREIGN KEY (user_id) REFERENCES users(id);

ALTER TABLE ONLY users
    ADD CONSTRAINT fksbg59w8q63i0oo53rlgvlcnjq FOREIGN KEY (department_id) REFERENCES departments(id);

ALTER TABLE ONLY task_logs
    ADD CONSTRAINT fkslumv6el85ys5ytniacscgusp FOREIGN KEY (task_id) REFERENCES tasks(id);

ALTER TABLE ONLY tasks
    ADD CONSTRAINT fkt1ph5sat39g9lpa4g5kl46tbv FOREIGN KEY (creator_id) REFERENCES users(id);

ALTER TABLE ONLY task_assignments
    ADD CONSTRAINT fktovnr7ljpnvn7expvkeptfyel FOREIGN KEY (assigned_by_id) REFERENCES users(id);

