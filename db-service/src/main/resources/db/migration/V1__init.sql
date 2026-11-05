CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT      NOT NULL UNIQUE,
    chat_id     BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE subscriptions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    title       VARCHAR(100),
    region      VARCHAR(100),
    salary_min  INTEGER,
    experience  VARCHAR(20),
    schedule    VARCHAR(20),
    employment  VARCHAR(20),
    keywords    VARCHAR(200),
    params_hash VARCHAR(64) NOT NULL,          -- хэш параметров для дедупликации поисков
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_subscriptions_user ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_hash ON subscriptions (params_hash) WHERE is_active;

CREATE TABLE user_favorites (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    vacancy_id VARCHAR(64)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'SAVED',
    note       VARCHAR(500),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (user_id, vacancy_id)
);
CREATE INDEX idx_favorites_vacancy ON user_favorites (vacancy_id);

-- TODO: vacancy_history и notification_log — партиционирование по месяцам (хранение 6 лет)
