-- 插入管理员账号
-- 密码 280976 使用 BCrypt 加密（强度 10）
INSERT INTO `user` (email, password, status, last_verified_at, create_time, update_time)
VALUES ('admin@alert.com', '$2a$10$/OhY.M5cdpF/.v2wrFIsn.O2eYqAtYS9Y1io5HuENXQfTuQWkRd96', 1, NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE
  password = VALUES(password),
  status = VALUES(status),
  last_verified_at = VALUES(last_verified_at),
  update_time = VALUES(update_time);
