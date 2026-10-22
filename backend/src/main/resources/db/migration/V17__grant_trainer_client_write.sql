-- I trainer gestiscono anagrafiche e dossier dei clienti.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.name = 'USER_WRITE'
WHERE r.name = 'TRAINER'
ON CONFLICT DO NOTHING;
