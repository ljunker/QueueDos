ALTER TABLE queuedos_tickets
    ADD COLUMN IF NOT EXISTS deleted_at text;
ALTER TABLE queuedos_tickets
    ADD COLUMN IF NOT EXISTS deleted_by_id text REFERENCES queuedos_users (id);

CREATE TABLE IF NOT EXISTS queuedos_ticket_commitments
(
    ticket_id text NOT NULL REFERENCES queuedos_tickets (id) ON DELETE CASCADE,
    user_id   text NOT NULL REFERENCES queuedos_users (id) ON DELETE CASCADE,
    PRIMARY KEY (ticket_id, user_id)
);

CREATE TABLE IF NOT EXISTS queuedos_activity_hooks
(
    id               text PRIMARY KEY,
    organization_id  text    NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    event_type       text    NOT NULL CHECK (event_type IN
                                             ('TICKET_CREATED', 'TICKET_UPDATED', 'TICKET_MOVED', 'COMMENT_ADDED',
                                              'COMMITMENT_CHANGED', 'TICKET_DELETED', 'TICKET_RESTORED')),
    webhook_url      text    NOT NULL,
    message_template text    NOT NULL,
    active           boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_queuedos_tickets_deleted ON queuedos_tickets (organization_id, deleted_at);
CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_commitments_user ON queuedos_ticket_commitments (user_id);
CREATE INDEX IF NOT EXISTS idx_queuedos_activity_hooks_org_event
    ON queuedos_activity_hooks (organization_id, event_type);
