ALTER TABLE queuedos_projects
    ADD COLUMN IF NOT EXISTS color text NOT NULL DEFAULT '#2563eb';
