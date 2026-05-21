CREATE TABLE IF NOT EXISTS queuedos_organizations
(
    id   text PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE IF NOT EXISTS queuedos_users
(
    id              text PRIMARY KEY,
    organization_id text    NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    email           text    NOT NULL,
    display_name    text    NOT NULL,
    role            text    NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    active          boolean NOT NULL,
    password_salt   text    NOT NULL,
    password_hash   text    NOT NULL,
    UNIQUE (organization_id, email)
);

CREATE TABLE IF NOT EXISTS queuedos_projects
(
    id                 text PRIMARY KEY,
    organization_id    text    NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    key                text    NOT NULL,
    name               text    NOT NULL,
    description        text    NOT NULL DEFAULT '',
    next_ticket_number integer NOT NULL DEFAULT 1 CHECK (next_ticket_number > 0),
    archived           boolean NOT NULL DEFAULT false,
    UNIQUE (organization_id, key)
);

CREATE TABLE IF NOT EXISTS queuedos_ticket_types
(
    id              text PRIMARY KEY,
    organization_id text NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    project_id      text NOT NULL REFERENCES queuedos_projects (id) ON DELETE CASCADE,
    name            text NOT NULL,
    description     text NOT NULL DEFAULT '',
    color           text NOT NULL,
    UNIQUE (project_id, name)
);

CREATE TABLE IF NOT EXISTS queuedos_workflows
(
    id              text PRIMARY KEY,
    organization_id text NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    project_id      text NOT NULL REFERENCES queuedos_projects (id) ON DELETE CASCADE,
    UNIQUE (project_id)
);

CREATE TABLE IF NOT EXISTS queuedos_workflow_statuses
(
    workflow_id text    NOT NULL REFERENCES queuedos_workflows (id) ON DELETE CASCADE,
    id          text    NOT NULL,
    name        text    NOT NULL,
    category    text    NOT NULL,
    sort_order  integer NOT NULL,
    PRIMARY KEY (workflow_id, id),
    UNIQUE (workflow_id, name),
    UNIQUE (workflow_id, sort_order)
);

CREATE TABLE IF NOT EXISTS queuedos_workflow_transitions
(
    workflow_id    text NOT NULL REFERENCES queuedos_workflows (id) ON DELETE CASCADE,
    id             text NOT NULL,
    from_status_id text,
    to_status_id   text NOT NULL,
    PRIMARY KEY (workflow_id, id),
    UNIQUE (workflow_id, from_status_id, to_status_id),
    FOREIGN KEY (workflow_id, from_status_id)
        REFERENCES queuedos_workflow_statuses (workflow_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workflow_id, to_status_id)
        REFERENCES queuedos_workflow_statuses (workflow_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS queuedos_workflow_transition_roles
(
    workflow_id   text    NOT NULL,
    transition_id text    NOT NULL,
    role          text    NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    sort_order    integer NOT NULL,
    PRIMARY KEY (workflow_id, transition_id, role),
    FOREIGN KEY (workflow_id, transition_id)
        REFERENCES queuedos_workflow_transitions (workflow_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS queuedos_workflow_transition_required_fields
(
    workflow_id   text    NOT NULL,
    transition_id text    NOT NULL,
    field_name    text    NOT NULL,
    sort_order    integer NOT NULL,
    PRIMARY KEY (workflow_id, transition_id, field_name),
    FOREIGN KEY (workflow_id, transition_id)
        REFERENCES queuedos_workflow_transitions (workflow_id, id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS queuedos_tickets
(
    id              text PRIMARY KEY,
    organization_id text    NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    project_id      text    NOT NULL REFERENCES queuedos_projects (id) ON DELETE CASCADE,
    number          integer NOT NULL CHECK (number > 0),
    key             text    NOT NULL,
    title           text    NOT NULL,
    description     text    NOT NULL DEFAULT '',
    status_id       text    NOT NULL,
    type_id         text    NOT NULL REFERENCES queuedos_ticket_types (id),
    priority        text    NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    assignee_id     text REFERENCES queuedos_users (id),
    reporter_id     text    NOT NULL REFERENCES queuedos_users (id),
    created_at      text    NOT NULL,
    updated_at      text    NOT NULL,
    UNIQUE (project_id, number),
    UNIQUE (organization_id, key)
);

CREATE TABLE IF NOT EXISTS queuedos_ticket_labels
(
    ticket_id  text    NOT NULL REFERENCES queuedos_tickets (id) ON DELETE CASCADE,
    label      text    NOT NULL,
    sort_order integer NOT NULL,
    PRIMARY KEY (ticket_id, label)
);

CREATE TABLE IF NOT EXISTS queuedos_ticket_comments
(
    id              text PRIMARY KEY,
    organization_id text NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    ticket_id       text NOT NULL REFERENCES queuedos_tickets (id) ON DELETE CASCADE,
    author_id       text NOT NULL REFERENCES queuedos_users (id),
    body            text NOT NULL,
    created_at      text NOT NULL
);

CREATE TABLE IF NOT EXISTS queuedos_ticket_changes
(
    id              text PRIMARY KEY,
    organization_id text NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    ticket_id       text NOT NULL REFERENCES queuedos_tickets (id) ON DELETE CASCADE,
    actor_id        text NOT NULL REFERENCES queuedos_users (id),
    field_name      text NOT NULL,
    old_value       text,
    new_value       text,
    created_at      text NOT NULL
);

CREATE TABLE IF NOT EXISTS queuedos_saved_ticket_filters
(
    id              text PRIMARY KEY,
    organization_id text NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    owner_id        text NOT NULL REFERENCES queuedos_users (id) ON DELETE CASCADE,
    name            text NOT NULL,
    view            text NOT NULL CHECK (view IN ('PROJECT_LIST', 'MY_TICKETS')),
    project_id      text REFERENCES queuedos_projects (id) ON DELETE CASCADE,
    filters         text NOT NULL
);

ALTER TABLE queuedos_workflow_transitions
    ALTER COLUMN from_status_id DROP NOT NULL;
ALTER TABLE queuedos_workflow_transitions
    ADD COLUMN IF NOT EXISTS global_transition boolean NOT NULL DEFAULT false;
ALTER TABLE queuedos_workflow_transitions
    ADD COLUMN IF NOT EXISTS allow_backward boolean NOT NULL DEFAULT true;
ALTER TABLE queuedos_tickets
    ADD COLUMN IF NOT EXISTS due_date text;
ALTER TABLE queuedos_tickets
    ADD COLUMN IF NOT EXISTS estimate integer CHECK (estimate IS NULL OR estimate >= 0);

CREATE INDEX IF NOT EXISTS idx_queuedos_users_organization ON queuedos_users (organization_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_projects_organization ON queuedos_projects (organization_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_types_project ON queuedos_ticket_types (project_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_workflows_project ON queuedos_workflows (project_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_organization ON queuedos_tickets (organization_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_project ON queuedos_tickets (project_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_status ON queuedos_tickets (status_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_assignee ON queuedos_tickets (assignee_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_labels_label ON queuedos_ticket_labels (label);
CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_comments_ticket ON queuedos_ticket_comments (ticket_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_changes_ticket ON queuedos_ticket_changes (ticket_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_saved_ticket_filters_owner ON queuedos_saved_ticket_filters (owner_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_queuedos_ticket_types_project_lower_name
    ON queuedos_ticket_types (project_id, lower(name));
CREATE UNIQUE INDEX IF NOT EXISTS uq_queuedos_saved_ticket_filters_context_lower_name
    ON queuedos_saved_ticket_filters (owner_id, view, coalesce(project_id, ''), lower(name));
