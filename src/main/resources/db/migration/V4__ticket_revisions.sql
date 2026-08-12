ALTER TABLE queuedos_tickets
    ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 1 CHECK (version > 0);

CREATE TABLE IF NOT EXISTS queuedos_ticket_revisions
(
    id              text PRIMARY KEY,
    organization_id text   NOT NULL REFERENCES queuedos_organizations (id) ON DELETE CASCADE,
    ticket_id       text   NOT NULL REFERENCES queuedos_tickets (id) ON DELETE CASCADE,
    version         bigint NOT NULL CHECK (version > 0),
    actor_id        text REFERENCES queuedos_users (id),
    action          text   NOT NULL CHECK (action IN
                                           ('BASELINE', 'CREATED', 'UPDATED', 'MOVED', 'COMMITMENT_CHANGED',
                                            'DELETED', 'RESTORED', 'REVISION_RESTORED')),
    source_version  bigint,
    snapshot        jsonb  NOT NULL,
    changes         jsonb  NOT NULL DEFAULT '[]'::jsonb,
    created_at      text   NOT NULL,
    UNIQUE (ticket_id, version)
);

CREATE INDEX IF NOT EXISTS idx_queuedos_ticket_revisions_ticket_version
    ON queuedos_ticket_revisions (ticket_id, version DESC);

INSERT INTO queuedos_ticket_revisions
    (id, organization_id, ticket_id, version, actor_id, action, source_version, snapshot, changes, created_at)
SELECT 'baseline-' || ticket.id,
       ticket.organization_id,
       ticket.id,
       1,
       NULL,
       'BASELINE',
       NULL,
       jsonb_build_object(
               'id', ticket.id,
               'organizationId', ticket.organization_id,
               'projectId', ticket.project_id,
               'number', ticket.number,
               'key', ticket.key,
               'title', ticket.title,
               'description', ticket.description,
               'statusId', ticket.status_id,
               'typeId', ticket.type_id,
               'priority', ticket.priority,
               'assigneeId', ticket.assignee_id,
               'committedUserIds', COALESCE(
                       (SELECT jsonb_agg(commitment.user_id ORDER BY commitment.user_id)
                        FROM queuedos_ticket_commitments commitment
                        WHERE commitment.ticket_id = ticket.id),
                       '[]'::jsonb),
               'labels', COALESCE(
                       (SELECT jsonb_agg(label.label ORDER BY label.sort_order)
                        FROM queuedos_ticket_labels label
                        WHERE label.ticket_id = ticket.id),
                       '[]'::jsonb),
               'dueDate', ticket.due_date,
               'estimate', ticket.estimate,
               'reporterId', ticket.reporter_id,
               'createdAt', ticket.created_at,
               'updatedAt', ticket.updated_at,
               'deletedAt', ticket.deleted_at,
               'deletedById', ticket.deleted_by_id,
               'version', 1),
       '[]'::jsonb,
       ticket.updated_at
FROM queuedos_tickets ticket
ON CONFLICT (ticket_id, version) DO NOTHING;
