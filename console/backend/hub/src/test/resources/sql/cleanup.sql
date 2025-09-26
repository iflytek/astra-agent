-- 清理测试数据
DELETE FROM user_notifications WHERE receiver_uid LIKE 'test-%';
DELETE FROM notifications WHERE title LIKE '测试%';
DELETE FROM user_broadcast_read WHERE receiver_uid LIKE 'test-%';