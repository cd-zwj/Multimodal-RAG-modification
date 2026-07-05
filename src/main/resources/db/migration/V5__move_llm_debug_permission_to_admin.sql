DELETE arp
FROM auth_role_permission arp
JOIN auth_role r ON r.id = arp.role_id
JOIN auth_permission p ON p.id = arp.permission_id
WHERE r.code = 'user'
  AND p.code = 'llm:debug';
