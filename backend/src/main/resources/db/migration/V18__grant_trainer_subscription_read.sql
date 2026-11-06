-- I trainer possono visualizzare la lista degli abbonamenti dei clienti.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'SUBSCRIPTION_READ'
WHERE r.name = 'TRAINER'
ON CONFLICT DO NOTHING;
