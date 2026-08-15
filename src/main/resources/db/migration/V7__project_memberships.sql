ALTER TABLE queuedos_users
    DROP CONSTRAINT IF EXISTS queuedos_users_role_check;

UPDATE queuedos_users SET role = 'SYSTEM_ADMIN' WHERE role = 'ADMIN';
UPDATE queuedos_users SET role = 'USER' WHERE role = 'MEMBER';

ALTER TABLE queuedos_users
    ADD CONSTRAINT queuedos_users_role_check CHECK (role IN ('SYSTEM_ADMIN', 'USER'));

CREATE TABLE IF NOT EXISTS queuedos_project_memberships
(
    project_id text NOT NULL REFERENCES queuedos_projects (id) ON DELETE CASCADE,
    user_id    text NOT NULL REFERENCES queuedos_users (id) ON DELETE CASCADE,
    role       text NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
    PRIMARY KEY (project_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_queuedos_project_memberships_user
    ON queuedos_project_memberships (user_id, project_id);

ALTER TABLE queuedos_workflow_transition_roles
    DROP CONSTRAINT IF EXISTS queuedos_workflow_transition_roles_role_check;

ALTER TABLE queuedos_workflow_transition_roles
    ADD CONSTRAINT queuedos_workflow_transition_roles_role_check CHECK (role IN ('ADMIN', 'MEMBER'));
