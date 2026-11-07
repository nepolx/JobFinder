package ru.vacancybot.db.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vacancybot.common.dto.SubscriptionDto;
import ru.vacancybot.db.entity.Subscription;
import ru.vacancybot.db.entity.User;
import ru.vacancybot.db.exception.NotFoundException;
import ru.vacancybot.db.mapper.SubscriptionMapper;
import ru.vacancybot.db.repository.SubscriptionRepository;
import ru.vacancybot.db.repository.UserRepository;

import java.util.List;

@Service
public class SubscriptionService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(UserService userService,
                               UserRepository userRepository,
                               SubscriptionRepository subscriptionRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Создаёт подписку. Новая подписка всегда активна. Пользователь создаётся при необходимости. */
    @Transactional
    public SubscriptionDto create(SubscriptionDto dto) {
        User user = userService.getOrCreate(dto.telegramUserId());
        Subscription subscription = new Subscription(user.getId());
        SubscriptionMapper.apply(dto, subscription);
        subscription.setActive(true);
        Subscription saved = subscriptionRepository.save(subscription);
        return SubscriptionMapper.toDto(saved, user.getTelegramId());
    }

    /**
     * Подписки пользователя. Если onlyActive = true, то только активные
     * (для команды /subscriptions). Если пользователя нет, вернёт пустой список.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionDto> list(Long telegramId, boolean onlyActive) {
        return userRepository.findByTelegramId(telegramId)
                .map(user -> subscriptionRepository.findByUserId(user.getId()).stream()
                        .filter(s -> !onlyActive || s.isActive())
                        .map(s -> SubscriptionMapper.toDto(s, user.getTelegramId()))
                        .toList())
                .orElse(List.of());
    }

    /** Кнопка «Изменить»: заменяет параметры поиска, статус active не меняется. */
    @Transactional
    public SubscriptionDto update(Long telegramId, Integer subscriptionId, SubscriptionDto dto) {
        User user = userService.getExisting(telegramId);
        Subscription subscription = find(user, subscriptionId);
        SubscriptionMapper.apply(dto, subscription);
        return SubscriptionMapper.toDto(subscriptionRepository.save(subscription), user.getTelegramId());
    }

    /** Включает или выключает подписку. Кнопка «Удалить» в боте = active=false. */
    @Transactional
    public SubscriptionDto setActive(Long telegramId, Integer subscriptionId, boolean active) {
        User user = userService.getExisting(telegramId);
        Subscription subscription = find(user, subscriptionId);
        subscription.setActive(active);
        return SubscriptionMapper.toDto(subscriptionRepository.save(subscription), user.getTelegramId());
    }

    /** Физическое удаление строки (бот использует деактивацию, это на крайний случай). */
    @Transactional
    public void delete(Long telegramId, Integer subscriptionId) {
        User user = userService.getExisting(telegramId);
        subscriptionRepository.delete(find(user, subscriptionId));
    }

    private Subscription find(User user, Integer subscriptionId) {
        return subscriptionRepository.findByIdAndUserId(subscriptionId, user.getId())
                .orElseThrow(() -> new NotFoundException("Подписка не найдена: " + subscriptionId));
    }
}