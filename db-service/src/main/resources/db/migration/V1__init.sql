-- =====================================================================
-- Схема БД db-service
-- =====================================================================

CREATE TABLE users (
                       id           serial PRIMARY KEY,
                       telegram_id  bigint NOT NULL UNIQUE,
                       created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE vacancies (
                           id               uuid PRIMARY KEY,          -- id из API
                           employer_name    text NOT NULL,
                           region_code      text NOT NULL,
                           region_name      text NOT NULL,
                           job_name         text NOT NULL,
                           salary_min       int,
                           salary_max       int,                       -- 0 из API превращается в NULL
                           schedule         text,                      -- текст для уведомления
                           schedule_code    text,                      -- rotation, flex, shift, irregular, part, full
                           employment_code  text,                      -- только если поле есть в API
                           experience_years int,
                           duty             text,
                           requirements     text,
                           vac_url          text NOT NULL,
                           modified_at      timestamptz NOT NULL,
                           search_vector    tsvector GENERATED ALWAYS AS (
                               setweight(to_tsvector('russian', coalesce(job_name,'')), 'A') ||
                               setweight(to_tsvector('russian', coalesce(duty,'') || ' ' || coalesce(requirements,'')), 'C')
                               ) STORED
);

CREATE TABLE subscriptions (
                               id               serial PRIMARY KEY,
                               user_id          integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               title            text,
                               region_code      text,
                               salary_min       int,
                               experience_from  int,
                               experience_to    int,
                               schedule_code    text,
                               employment_code  text,
                               keywords         text,
                               query_tsq        tsquery GENERATED ALWAYS AS (
                                   plainto_tsquery('russian',
                                                   NULLIF(trim(coalesce(title,'') || ' ' || coalesce(keywords,'')), ''))
                                   ) STORED,
                               is_active        boolean NOT NULL DEFAULT true,
                               created_at       timestamptz NOT NULL DEFAULT now()
);

-- [FIX 1] избранное с заметкой и статусом отклика
CREATE TABLE user_favorites (
                                user_id     integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                vacancy_id  uuid    NOT NULL REFERENCES vacancies(id),
                                status      text    NOT NULL DEFAULT 'saved'
                                    CHECK (status IN ('saved', 'applied', 'waiting')),
    -- saved = просто сохранена, applied = откликнулся, waiting = жду ответа
                                note        text,
                                created_at  timestamptz NOT NULL DEFAULT now(),
                                updated_at  timestamptz NOT NULL DEFAULT now(),
                                PRIMARY KEY (user_id, vacancy_id)
);

-- [FIX 2] история изменений вакансий (партиции по времени обнаружения изменения)
-- В diff хранится только изменившееся: {"salary_min": {"old": 50000, "new": 70000}}
-- Для 'created' changes может быть NULL.
CREATE TABLE vacancy_changes (
                                 id          bigint GENERATED ALWAYS AS IDENTITY,
                                 vacancy_id  uuid        NOT NULL REFERENCES vacancies(id),
                                 change_type text        NOT NULL
                                     CHECK (change_type IN ('created', 'updated')),
                                 changes     jsonb,
                                 changed_at  timestamptz NOT NULL DEFAULT now(),
                                 PRIMARY KEY (id, changed_at)                -- ключ партиционирования обязан входить в PK
) PARTITION BY RANGE (changed_at);

-- [FIX 3] журнал уведомлений: одно изменение одному пользователю ровно один раз
-- changed_at дублируется из vacancy_changes: он нужен для составного FK и партиционирования.
-- Так как id изменения глобально уникален, PRIMARY KEY (change_id, user_id, changed_at)
-- фактически гарантирует «одно изменение — один пользователь — один раз».
CREATE TABLE notification_log (
                                  change_id       bigint      NOT NULL,
                                  changed_at      timestamptz NOT NULL,
                                  user_id         integer     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                  subscription_id integer     REFERENCES subscriptions(id) ON DELETE SET NULL,
                                  status          text        NOT NULL DEFAULT 'pending'
                                      CHECK (status IN ('pending', 'sent', 'failed')),
                                  attempts        int         NOT NULL DEFAULT 0,
                                  last_error      text,
                                  created_at      timestamptz NOT NULL DEFAULT now(),
                                  sent_at         timestamptz,
                                  PRIMARY KEY (change_id, user_id, changed_at),
                                  FOREIGN KEY (change_id, changed_at) REFERENCES vacancy_changes (id, changed_at)
) PARTITION BY RANGE (changed_at);

-- Партиции по годам (дальше создавать новые миграцией или pg_partman)
DO $$
DECLARE y int;
BEGIN
FOR y IN 2026..2028 LOOP
    EXECUTE format(
      'CREATE TABLE vacancy_changes_%s PARTITION OF vacancy_changes
         FOR VALUES FROM (%L) TO (%L)', y, y || '-01-01', (y + 1) || '-01-01');
EXECUTE format(
        'CREATE TABLE notification_log_%s PARTITION OF notification_log
           FOR VALUES FROM (%L) TO (%L)', y, y || '-01-01', (y + 1) || '-01-01');
END LOOP;
END $$;

CREATE TABLE vacancy_changes_default   PARTITION OF vacancy_changes   DEFAULT;
CREATE TABLE notification_log_default  PARTITION OF notification_log  DEFAULT;

-- Индексы
CREATE INDEX vac_modified    ON vacancies (modified_at);                 -- курсор синхронизации
CREATE INDEX vac_fts         ON vacancies USING gin (search_vector);
CREATE INDEX sub_region      ON subscriptions (region_code) WHERE is_active;
CREATE INDEX sub_user        ON subscriptions (user_id);
CREATE INDEX fav_vacancy     ON user_favorites (vacancy_id);
CREATE INDEX chg_vacancy     ON vacancy_changes (vacancy_id, changed_at DESC);
CREATE INDEX notif_pending   ON notification_log (created_at) WHERE status <> 'sent';
CREATE INDEX notif_user      ON notification_log (user_id);