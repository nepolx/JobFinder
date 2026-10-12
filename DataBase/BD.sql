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
  closed_at        timestamptz,
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

CREATE INDEX vac_modified ON vacancies (modified_at);          -- курсор синхронизации
CREATE INDEX vac_fts      ON vacancies USING gin (search_vector);
CREATE INDEX sub_region   ON subscriptions (region_code) WHERE is_active;
CREATE INDEX sub_user     ON subscriptions (user_id);
CREATE INDEX fav_vacancy  ON user_favorites (vacancy_id);

