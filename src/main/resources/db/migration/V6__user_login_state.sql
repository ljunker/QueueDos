ALTER TABLE queuedos_users
    ADD COLUMN IF NOT EXISTS local_login_enabled boolean NOT NULL DEFAULT true;

ALTER TABLE queuedos_users
    ADD COLUMN IF NOT EXISTS must_change_password boolean NOT NULL DEFAULT false;
