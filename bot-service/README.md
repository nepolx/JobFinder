# bot-service

Telegram-бот: webhook, FSM `/subscribe`, подписки, избранное. Модуль stateless (контекст диалога — в Redis).
Данные берёт у db-service по REST (`client/`, контракт — `API.md`, часть A). Если db-service не запущен, `BOT_STUBS_ENABLED=true` включает заглушки в памяти (`stub/`).

## Запуск локально (без docker compose)

```bash
export BOT_TOKEN=...                                   # токен от @BotFather
export BOT_WEBHOOK_URL=https://xxxx.ngrok-free.app/webhook   # ngrok http 8080; адрес должен заканчиваться на /webhook
export BOT_WEBHOOK_SECRET=abc123_secret                # необязательно
export DB_SERVICE_URL=http://localhost:8081            # по умолчанию так же; нужен запущенный db-service
# нет db-service? export BOT_STUBS_ENABLED=true
mvn -pl bot-service spring-boot:run
```

Без `BOT_TOKEN` приложение стартует, но к Telegram не подключается. Тесты: `mvn -pl bot-service test`.

Проверка уведомлений без notifier: `bot.dev-endpoints=true`, затем
`curl -X POST localhost:8080/dev/notify/<telegram_id>` (только локально!).

## Связь с db-service

Интерфейсы в `port/` реализованы HTTP-клиентами в `client/` (`HttpUserService`, `HttpSubscriptionService`, `HttpFavoriteService`).
Они включаются при `bot.stubs.enabled=false` (значение по умолчанию). Карточки для `/favorites` собираются двумя запросами:
`GET .../favorites` и `GET /api/vacancies?ids=...`.

Не на REST: `RegionClassifier` всегда работает на заглушке, потому что эндпоинта регионов в db-service пока нет (API.md, часть B).
Когда он появится, добавьте `HttpRegionClassifier` (кэш в памяти) и уберите `StubRegionClassifier`.

Для `GET /api/vacancies` бот использует свой record `VacancyResponse`. Когда `VacancyDto` попадёт в `common`, его можно заменить на него.

Для нескольких реплик: `BOT_SESSION_STORE=redis` (`RedisSessionStore`, TTL 6 часов).

## Контракт кнопок для notifier-service

Бот обрабатывает кнопки под уведомлениями, поэтому notifier должен выставлять именно такой `callback_data`
(раскладка как в «Tinder»: ряд из двух кнопок, ниже «Отписаться»):

| Кнопка | callback_data | Что делает бот |
|---|---|---|
| 👎 Не интересно | `vac_skip_{vacancyId}` | удаляет уведомление из чата |
| 👍 В избранное | `fav_add_{vacancyId}` | кладёт в избранное, убирает 👍 и 👎 |
| Отписаться | `sub_del_{subscriptionId}` | удаляет подписку |

`vacancyId` — буквы, цифры, `-`, `_`, до 40 символов; весь `callback_data` не длиннее 64 байт.

## Меню под полем ввода

После `/start` (и `/help`) под полем ввода появляется постоянное меню: ➕ Новая подписка, 📋 Мои подписки,
🔥 Подборка, ⭐ Избранное, ❓ Помощь. Нажатие работает как команда и прерывает незаконченный мастер.

## Мастер подписки не засоряет чат

Вопросы бота и ответы пользователя запоминаются в сессии и удаляются, когда мастер закончился
(подтверждение, отмена, другая команда или перезапуск). В чате остаётся только итог. Если сервис упал при подтверждении,
сообщения остаются, и можно нажать «Создать подписку» ещё раз.

## Подборка (/browse): 👍 / 👎

Карточки вакансий по одной: 👍 кладёт в избранное, 👎 пропускает, карточка заменяется следующей, «Закончить» останавливает.
Оценённые вакансии не повторяются.

Нужны два новых эндпоинта в db-service (в API.md их пока нет, **предложение на согласование**):

| Метод | Путь | Ответ |
|---|---|---|
| GET | `/api/users/{telegramId}/feed/next` | `200` + `VacancyDto` следующей вакансии под активные подписки, которую пользователь ещё не оценивал; `204`, если показывать нечего |
| POST | `/api/users/{telegramId}/feed/{vacancyId}/seen` | `204`; вакансия больше не показывается (вызывается и для 👍, и для 👎) |

Пока эндпоинтов нет (`404`/`405`), бот отвечает «Подборка пока недоступна» и продолжает работать. С `BOT_STUBS_ENABLED=true` подборка идёт из примеров.

## Структура

```
handler/   UpdateRouter → CommandHandler, WizardHandler (FSM), SubscriptionHandler, FavoriteHandler, FeedHandler
fsm/       DialogState, Session, SessionStore (память / Redis), InputValidator
view/      MessageFormatter (все тексты), Keyboards, MenuButtons, CallbackData
telegram/  TelegramClient, RestTelegramClient, WebhookRegistrar, dto/
port/      контракты с db-service      stub/  заглушки
```
