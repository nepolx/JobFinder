# Notifier (Java 21, Spring Boot 3)

Читает события изменений вакансий из RabbitMQ, определяет получателей в PostgreSQL, отправляет
уведомления в Telegram и пишет результат в журнал `notification_log`. Схему БД ведёт db-service
(Flyway), Notifier её **не создаёт и не меняет**.

```
Poller --> RabbitMQ (vacancy.events) --> Notifier --> Telegram
                                            |--> Redis       (общие счётчики лимитов)
                                            '--> PostgreSQL  (получатели, журнал notification_log)
```

## Что изменено по замечаниям Лёши (БД)

| Замечание | Что сделано |
|---|---|
| Убрать `v.closed_at IS NULL` | Условие удалено из `SQL_SUBSCRIBERS` |
| Не запускать `002_notifier.sql` | Файла миграции в проекте нет; `is_blocked` и статус `blocked` берутся из V2 |
| `notification_log` вместо `notifications` | Все запросы переписаны под ключ `(change_id, changed_at, user_id)`; `id` и `vacancy_id` не используются |
| `changed_at` в событии | Обязательное поле, ISO-8601 с часовым поясом; обрезается до микросекунд (точность `timestamptz`) |
| `diff` | Формат прежний `{"salary_min": [50000, 70000]}`; тип `closed` обработан, но приходить не будет |
| Интеграционные тесты | Переписаны под `notification_log` и `is_blocked` (Testcontainers) |

## Контракт события (для Poller)

Очередь `vacancy.events`, JSON, публикация через default exchange с routing key `vacancy.events`:

```json
{"change_id": 123, "vacancy_id": "3f2b8c1e-6a4d-4e0a-9c55-0d1f2a3b4c5d", "type": "updated",
 "changed_at": "2026-11-08T12:00:00Z",
 "diff": {"salary_min": [50000, 70000], "schedule": ["Полный день", "Гибкий"]}}
```

- `change_id` и `changed_at` берутся из ответа `POST /api/vacancies/sync` **без изменений**: вместе они
  служат ключом журнала. Время должно содержать часовой пояс (`Z` или `+03:00`), без него событие отбрасывается.
- `type`: `created` или `updated` (`closed` поддержан, но не используется); `diff` нужен только для `updated`.
- В сообщении показываются поля `salary_min`, `salary_max`, `schedule`, `experience_years`, `employer_name`,
  `region_name`, `job_name`; для `duty` и `requirements` пишется только «изменились».
- Вакансия к моменту обработки уже должна лежать в `vacancies`. Публиковать событие нужно **после** записи в БД.
- Очереди объявляет Notifier (с dead-letter-аргументами). Если Poller тоже их объявляет, аргументы должны
  совпадать, иначе будет `PRECONDITION_FAILED`; проще, чтобы Poller только публиковал.

## Кому и в каком виде уходит событие

Одно и то же событие может уйти разным людям в разном виде.

| Событие | Кому | Что видит пользователь |
|---|---|---|
| `created` | подписчики, которым вакансия подходит | «🆕 Новая вакансия по вашей подписке» |
| `updated` | владельцы избранного | «✏️ Вакансия из избранного изменилась» + что именно изменилось (diff) |
| `updated` | подписчики, которым вакансия подходит и **о ней им ещё ничего не отправляли** | «🆕 Новая вакансия по вашей подписке» (без diff: для них она новая) |
| `updated` | подписчики, которые уже получали эту вакансию, но не добавили в избранное | ничего (иначе «новая» приходила бы после каждой правки) |
| `closed` | владельцы избранного | «🚫 Вакансия закрыта» |

«Уже получал» = в `notification_log` есть запись `sent` по любому изменению этой вакансии (связь через историю
`vacancy_changes`). Владелец избранного, он же подписчик, получает одно сообщение («изменилась»).
Второй вариант поведения (только владельцы избранного, как в требовании 3) включается переменной
`NOTIFY_SUBSCRIBERS_ON_UPDATE=false`.

Не делается: пользователь, который подписался позже, не получает уже существующие вакансии, пока те не обновятся
(это отдельная функция «показать текущие вакансии при подписке»).

## Что должно быть в БД (проверить с Лёшей)

Notifier использует:

- `notification_log`: ключ `(change_id, user_id, changed_at)` и колонки `status` (`pending` по умолчанию; значения
  `pending|sent|failed|blocked`), `attempts` (по умолчанию 0), `last_error`, `sent_at`.
- `vacancy_changes` (история): `id`, `vacancy_id`, `changed_at`; `notification_log.change_id` = `vacancy_changes.id`.
  Нужен индекс `(vacancy_id, changed_at)`. Через неё определяется, получал ли пользователь вакансию раньше.
- `users.is_blocked`. Bot API должен сбрасывать его в `false`, когда пользователь снова нажмёт `/start`.
- `vacancies`, `subscriptions`, `user_favorites` с колонками, которые использует подбор получателей
  (`region_code`, `salary_*`, `experience_years`, `schedule_code`, `employment_code`, `search_vector`, `query_tsq`).

Если названия таблиц и колонок отличаются, правится только SQL в `JdbcNotificationRepository`.

### Годовые партиции

Журнал и история партиционированы по годам (2026-2032 и запасная `default`). Партиции создаёт и поддерживает
db-service, Notifier их **не создаёт**: он только берёт `changed_at` из события и использует его в ключе
`(change_id, user_id, changed_at)`.

- Запись попадает в партицию года изменения; все обращения к журналу идут по полному ключу с `changed_at`, поэтому
  PostgreSQL читает только нужную партицию. Проверка «получал ли раньше» связывает журнал с историей по
  `(id, changed_at)` и учитывает все сохранённые годы.
- Событие с `changed_at` вне созданных партиций не теряется: запись уходит в `default`, и Notifier находит её
  по тому же ключу.
- Если срок хранения ограничен удалением старых партиций, пользователи, чьи записи удалены, снова увидят вакансию как
  «новую».

## Как работает обработка события

1. Загружает вакансию и находит получателей (таблица выше).
2. `INSERT ... ON CONFLICT DO NOTHING` в `notification_log`, затем выбирает записи со статусом `pending`.
3. Для каждой: токены лимитов (на чат и общий, Redis), отправка, повторы. Результат: `sent`, `blocked`
   (ещё `users.is_blocked = true`), `failed`.
4. Если часть уведомлений не удалось доставить: сообщение отклоняется (`nack` без возврата) и через
   `RETRY_DELAY_MS` возвращается из очереди `vacancy.events.retry`. После `MAX_REDELIVERIES` кругов оставшиеся
   записи помечаются `failed`. Повторная обработка шлёт только тем, у кого осталось `pending`.

Возможен один дубль, если сообщение ушло в Telegram, а запись в журнал не удалась (at-least-once).

## Настройка (переменные окружения)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `TELEGRAM_BOT_TOKEN`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | обязательные | `DB_URL` вида `jdbc:postgresql://postgres:5432/app` |
| `REDIS_HOST`, `RABBITMQ_HOST` | `redis`, `rabbitmq` | адреса сервисов |
| `GLOBAL_RATE` / `GLOBAL_BURST` | 25 / 5 | общий лимит сообщений/с на все копии (у Telegram около 30) |
| `PER_CHAT_RATE` | 1 | сообщений/с в один чат |
| `EVENT_CONCURRENCY` | 10 | событий обрабатывается одновременно |
| `SEND_CONCURRENCY` | 10 | получателей одного события параллельно |
| `MAX_ATTEMPTS` | 5 | попыток отправки одному получателю |
| `NOTIFY_SUBSCRIBERS_ON_UPDATE` | true | при изменении вакансии слать подписчикам, которые её ещё не видели, как «новую» |
| `RETRY_DELAY_MS` / `MAX_REDELIVERIES` | 60000 / 5 | задержка и число кругов повторной доставки |

Ключи лимитов в Redis: `rl:global` и `rl:chat:<id>`. Если Bot API шлёт ответы пользователям, его отправки тоже
входят в лимит Telegram: пусть берёт токены из `rl:global` (класс `RedisRateLimiter`).

## Запуск и тесты

```bash
mvn test        # unit-тесты (76 штук, внешние сервисы не нужны)
mvn verify      # + интеграционные тесты SQL на PostgreSQL в Testcontainers (нужен Docker)

docker build -t notifier .                      # фрагмент для compose: docker-compose.notifier.yml
docker compose up -d --scale notifier=3
```

Схема для интеграционных тестов лежит в `src/test/resources/test-schema.sql` (годовые партиции 2026-2032 и default). Замените её на реальные
миграции Flyway из db-service, чтобы тесты проверяли ту же схему, что и в проде.

## Структура

```
src/main/java/ru/vacancy/notifier/
  model/     ChangeEvent (разбор и валидация), ChangeType, Vacancy, Recipient
  format/    MessageFormatter: текст уведомления, HTML-экранирование, лимит 4096 символов
  limit/     TokenBucket, InMemoryRateLimiter, RedisRateLimiter (Lua), CompositeRateLimiter
  retry/     RetryExecutor: экспоненциальная задержка, retry_after
  telegram/  TelegramResponses (ответ -> тип ошибки), HttpTelegramSender, исключения
  db/        NotificationRepository, JdbcNotificationRepository (весь SQL здесь)
  service/   NotificationService: событие -> получатели -> отправка -> журнал
  mq/        EventMessageProcessor (решение ack/nack), EventListener, JacksonEventParser
  config/    NotifierProperties, AppConfig (очереди, бины)
```
