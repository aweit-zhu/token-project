-- 示範帳號，僅供開發 / 展示使用。密碼：alice / alice123、admin / admin123
-- 每次啟動都會執行：只補缺少的資料，不覆蓋既有資料。

INSERT INTO roles (name)
SELECT 'USER' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'USER');
INSERT INTO roles (name)
SELECT 'ADMIN' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO users (username, password, enabled)
SELECT 'alice', '{bcrypt}$2a$10$oasznHzWWSPd2SELS3MWG.a1CXFTjICJedCkePx.fmZ2U8tiFvoTG', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'alice');
INSERT INTO users (username, password, enabled)
SELECT 'admin', '{bcrypt}$2a$10$2Knp5uIZKTjBRBaQbXOwquUSfAsWBXZOQg4HxJiMYO8hBQwidwCaq', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'alice' AND r.name = 'USER'
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin' AND r.name IN ('USER', 'ADMIN')
  AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id);
