-- Схема для интеграционных тестов Notifier.
-- ЗАМЕНИТЕ на реальные миграции Flyway из db-service (скопируйте V*.sql сюда и выполняйте по порядку),
-- чтобы тесты проверяли именно ту схему, которая будет в проде.

CREATE TABLE users (
  id           serial PRIMARY KEY,
  telegram_id  bigint NOT NULL UNIQUE,
  is_blocked   boolean NOT NULL DEFAULT false,
  created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE vacancies (
  id               uuid PRIMARY KEY,
  employer_name    text NOT NULL,
  region_code      text NOT NULL,
  region_name      text NOT NULL,
  job_name         text NOT NULL,
  salary_min       int,
  salary_max       int,
  schedule         text,
  schedule_code    text,
  employment_code  text,
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

CREATE TABLE user_favorites (
  user_id     integer NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  vacancy_id  uuid    NOT NULL REFERENCES vacancies(id),
  created_at  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, vacancy_id)
);

-- История изменений вакансий (пишет db-service). Партиции годовые.
-- Название и колонки взяты по договорённости; сверьте с реальной схемой.
CREATE TABLE vacancy_changes (
  id          bigserial,
  vacancy_id  uuid        NOT NULL,
  changed_at  timestamptz NOT NULL,
  change_type text        NOT NULL,
  diff        jsonb,
  PRIMARY KEY (id, changed_at)
) PARTITION BY RANGE (changed_at);
CREATE INDEX vacancy_changes_vacancy ON vacancy_changes (vacancy_id, changed_at);
CREATE TABLE vacancy_changes_2026 PARTITION OF vacancy_changes
  FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');
CREATE TABLE vacancy_changes_2027 PARTITION OF vacancy_changes
  FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');

-- Журнал уведомлений: партиции годовые, ключ (change_id, user_id, changed_at).
-- Партиции по умолчанию нет: событие из года без партиции должно падать, а не теряться молча.
CREATE TABLE notification_log (
  change_id   bigint      NOT NULL,
  user_id     integer     NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  changed_at  timestamptz NOT NULL,
  status      text        NOT NULL DEFAULT 'pending',
  attempts    integer     NOT NULL DEFAULT 0,
  last_error  text,
  sent_at     timestamptz,
  PRIMARY KEY (change_id, user_id, changed_at)
) PARTITION BY RANGE (changed_at);
CREATE TABLE notification_log_2026 PARTITION OF notification_log
  FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');
CREATE TABLE notification_log_2027 PARTITION OF notification_log
  FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');
