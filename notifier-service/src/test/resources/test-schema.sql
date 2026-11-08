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

-- Журнал уведомлений: партиционирован по changed_at, ключ (change_id, user_id, changed_at).
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

CREATE TABLE notification_log_default PARTITION OF notification_log DEFAULT;
