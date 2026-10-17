BEGIN;

INSERT INTO permission (name) VALUES
    ('goods-requests.approve')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM role
JOIN permission ON permission.name IN (
    'goods-requests.read',
    'goods-requests.approve'
)
WHERE role.name = 'BOOKKEEPER'
ON CONFLICT DO NOTHING;

COMMIT;
