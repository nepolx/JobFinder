-- Поддержка notifier.

-- Пользователь заблокировал бота: больше ему не пишем.
-- Сбрасывается в false, когда пользователь снова обращается к боту (/start, подписка, избранное).
ALTER TABLE users ADD COLUMN is_blocked boolean NOT NULL DEFAULT false;

-- В журнале уведомлений нужен статус blocked (бот заблокирован пользователем).
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_status_check;
ALTER TABLE notification_log ADD CONSTRAINT notification_log_status_check
    CHECK (status IN ('pending', 'sent', 'failed', 'blocked'));