ALTER TABLE announcements ADD COLUMN IF NOT EXISTS recipient_count integer NOT NULL DEFAULT 0;

-- Announcement permissions. V7 granted ADMIN only what existed then, so grant explicitly.
INSERT INTO permissions (id, name, module, action, is_active, description, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'announcements:view', 'ANNOUNCEMENT', 'view', true,
        'View all announcements, stats and analytics (admin)', now(), now()),
    (gen_random_uuid(), 'announcements:create', 'ANNOUNCEMENT', 'create', true,
        'Create and duplicate announcements', now(), now()),
    (gen_random_uuid(), 'announcements:update', 'ANNOUNCEMENT', 'update', true,
        'Edit and archive announcements', now(), now()),
    (gen_random_uuid(), 'announcements:publish', 'ANNOUNCEMENT', 'publish', true,
        'Publish announcements and send their notifications', now(), now()),
    (gen_random_uuid(), 'announcements:delete', 'ANNOUNCEMENT', 'delete', true,
        'Delete announcements', now(), now())
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name LIKE 'announcements:%'
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;
