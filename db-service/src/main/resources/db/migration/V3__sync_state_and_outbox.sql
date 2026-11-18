-- V3: курсор синхронизации и отметки публикации событий (outbox) для worker.

-- 1) Служебное хранилище значений синхронизации
CREATE TABLE sync_state (
                            state_key   text        PRIMARY KEY,
                            state_value text        NOT NULL,
                            updated_at  timestamptz NOT NULL DEFAULT now()
);

-- 2) Отметки публикации события в RabbitMQ.
--    ALTER по партиционированной таблице добавляет колонки сразу во все партиции.
ALTER TABLE vacancy_changes
    ADD COLUMN published_at       timestamptz,
    ADD COLUMN publish_attempts   int  NOT NULL DEFAULT 0,
    ADD COLUMN last_publish_error text;

-- Всё, что было в истории до этой миграции, уже обработано: иначе оно попало бы в «неопубликованные».
UPDATE vacancy_changes SET published_at = now() WHERE published_at IS NULL;

-- 3) Частичный индекс: быстро находить только неопубликованные события.
CREATE INDEX chg_unpublished ON vacancy_changes (id) WHERE published_at IS NULL;