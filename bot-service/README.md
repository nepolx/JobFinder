# bot-service

Telegram-бот: webhook, FSM `/subscribe`, подписки, избранное. Модуль stateless (контекст диалога — в Redis).
Пока db-service не готов, вместо него работают заглушки в памяти (`stub/`).

## Запуск локально (без docker compose)

```bash
export BOT_TOKEN=...                                   # токен от @BotFather
export BOT_WEBHOOK_URL=https://xxxx.ngrok-free.app/webhook   # ngrok http 8080; адрес должен заканчиваться на /webhook
export BOT_WEBHOOK_SECRET=abc123_secret                # необязательно
mvn -pl bot-service spring-boot:run
```

Без `BOT_TOKEN` приложение стартует, но к Telegram не подключается. Тесты: `mvn -pl bot-service test`.

Проверка уведомлений без notifier: `bot.dev-endpoints=true`, затем
`curl -X POST localhost:8080/dev/notify/<telegram_id>` (только локально!).

## Что подключить вместо заглушек

Контракт с db-service — интерфейсы в `port/` (`UserService`, `SubscriptionService`, `FavoriteService`, `RegionClassifier`).
Реализации будут HTTP-клиентами в `client/`; затем `bot.stubs.enabled=false`. Остальной код бота не меняется.

Для нескольких реплик: `BOT_SESSION_STORE=redis` (`RedisSessionStore`, TTL 6 часов).

## Контракт кнопок для notifier-service

Бот обрабатывает кнопки под уведомлениями, поэтому notifier должен выставлять именно такой `callback_data`:

| Кнопка | callback_data |
|---|---|
| В избранное (новая вакансия) | `fav_add_{vacancyId}` |
| Отписаться (новая вакансия) | `sub_del_{subscriptionId}` |

`vacancyId` — буквы, цифры, `-`, `_`, до 40 символов; весь `callback_data` не длиннее 64 байт.

## Структура

```
handler/   UpdateRouter → CommandHandler, WizardHandler (FSM), SubscriptionHandler, FavoriteHandler
fsm/       DialogState, Session, SessionStore (память / Redis), InputValidator
view/      MessageFormatter (все тексты), Keyboards, CallbackData
telegram/  TelegramClient, RestTelegramClient, WebhookRegistrar, dto/
port/      контракты с db-service      stub/  заглушки
```
