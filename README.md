# vacancy-bot

Telegram-бот для мониторинга вакансий портала «Работа в России» (trudvsem.ru):
подписки на поиск, избранное с заметками, уведомления о новых и изменённых вакансиях.

## Модули и владельцы

| Модуль            | Порт | Что делает                                              | Кто |
|-------------------|------|---------------------------------------------------------|-----|
| common            |  —   | DTO, события RabbitMQ, константы (меняем только PR-ом)   | все |
| bot-service       | 8080 | Telegram webhook, команды, FSM `/subscribe`, callback-и | ?   |
| db-service        | 8081 | REST API к PostgreSQL: пользователи, подписки, избранное | ?   |
| worker-service    | 8082 | Опрос trudvsem.ru, дедупликация, события в RabbitMQ      | ?   |
| notifier-service  | 8083 | Чтение очереди, отправка в Telegram с rate limit/retry  | ?   |

Поток: `Telegram -> bot -> db-service`, `worker -> trudvsem.ru`, `worker -> RabbitMQ -> notifier -> Telegram`.

## Запуск

```bash
cp .env.example .env
docker compose up -d          # postgres, rabbitmq, redis
mvn clean install             # сборка всех модулей
mvn -pl db-service spring-boot:run
```

## Git-флоу
- `main` — стабильная ветка, `develop` — интеграция
- ветки `feature/<module>-<описание>`, слияние через PR с ревью одним из команды
