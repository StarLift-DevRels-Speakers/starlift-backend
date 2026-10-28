package ru.shaxowskiy.javaspeakerclub.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Уведомления пользователя о заявках, приглашениях, отзывах и мероприятиях.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SpeakerAccessGuard accessGuard;

    public record NotificationResponse(UUID id, String text, LocalDateTime createdDate, boolean read) {
    }

    public record MarkReadRequest(UUID id) {
    }

    @Transactional
    public void notify(Long userId, String text) {
        notificationRepository.insert(userId, text);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMine() {
        return notificationRepository.findByUserId(accessGuard.currentUserId()).stream()
                .map(record -> new NotificationResponse(
                        record.getId(),
                        record.getText(),
                        record.getCreatedDate(),
                        Boolean.TRUE.equals(record.getIsRead())
                ))
                .toList();
    }

    /**
     * Без id — помечает прочитанными все уведомления текущего пользователя.
     */
    @Transactional
    public void markRead(UUID notificationId) {
        notificationRepository.markRead(accessGuard.currentUserId(), notificationId);
    }
}
