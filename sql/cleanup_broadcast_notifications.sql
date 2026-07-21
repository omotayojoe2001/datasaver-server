-- Optional: remove old broadcast test notifications (shown to all users before the fix)
-- Review rows first:
-- SELECT id, title, body, created_at FROM notifications WHERE target_phone IS NULL ORDER BY created_at DESC;

DELETE FROM notifications
WHERE target_phone IS NULL;
