package ru.shaxowskiy.javaspeakerclub.notification;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Notifications;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.NotificationsRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class NotificationRepository {

    private final DSLContext dsl;

    public void insert(Long userId, String text) {
        dsl.insertInto(Notifications.NOTIFICATIONS)
                .set(Notifications.NOTIFICATIONS.ID, UUID.randomUUID())
                .set(Notifications.NOTIFICATIONS.USER_ID, userId)
                .set(Notifications.NOTIFICATIONS.TEXT, text)
                .set(Notifications.NOTIFICATIONS.IS_READ, false)
                .set(Notifications.NOTIFICATIONS.CREATED_DATE, LocalDateTime.now())
                .execute();
    }

    public List<NotificationsRecord> findByUserId(Long userId) {
        return dsl.selectFrom(Notifications.NOTIFICATIONS)
                .where(Notifications.NOTIFICATIONS.USER_ID.eq(userId))
                .orderBy(Notifications.NOTIFICATIONS.CREATED_DATE.desc())
                .fetch();
    }

    public void markRead(Long userId, UUID notificationId) {
        var condition = Notifications.NOTIFICATIONS.USER_ID.eq(userId);
        if (notificationId != null) {
            condition = condition.and(Notifications.NOTIFICATIONS.ID.eq(notificationId));
        }
        dsl.update(Notifications.NOTIFICATIONS)
                .set(Notifications.NOTIFICATIONS.IS_READ, true)
                .where(condition)
                .execute();
    }
}
