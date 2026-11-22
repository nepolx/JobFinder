# vacancy-bot

Telegram-бот для мониторинга вакансий портала «Работа в России» (trudvsem.ru): подписки на поиск, избранное с заметками, уведомления о новых и изменённых вакансиях.

## Модули

| Модуль | Порт | Что делает | Кто |
|---|---|---|---|
| `common` | — | общие DTO и константы (меняем только PR-ом) | все |
| `bot-service` | 8080 | Telegram webhook, команды, FSM `/subscribe` | Ангелина |
| `db-service` | 8081 | REST API к PostgreSQL, миграции Flyway | Полина |
| `worker-service` | 8082 | опрос trudvsem.ru, события в RabbitMQ | Маша |
| `notifier-service` | 8083 | чтение очереди, отправка в Telegram | Леша |

## Поток данных

```
Telegram -> bot-service -> db-service -> PostgreSQL
trudvsem.ru -> worker-service -> db-service (сравнение, история) -> RabbitMQ -> notifier-service -> Telegram
```

Worker отправляет вакансии в `db-service`, тот сравнивает их со старой версией и возвращает события `created` и `updated`. Worker публикует их в RabbitMQ (outbox: событие не теряется при сбое), notifier подбирает подписчиков запросом к БД и отправляет сообщения.
Контракт REST API: [`db-service/API.md`](db-service/API.md).

## Запуск

Нужны Docker Desktop, JDK 21, Maven, токен бота и ngrok.

```powershell
mvn -pl common -am install -DskipTests
ngrok http 8080                           # https-адрес + /webhook -> BOT_WEBHOOK_URL в .env
docker compose up -d --build
docker compose ps                         # все сервисы Up
```

