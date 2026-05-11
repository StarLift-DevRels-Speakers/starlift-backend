package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserMedia;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UserMediaRecord;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserMediaRepository {

    private final DSLContext dsl;

    public UserMediaRecord create(UUID id, Long userId, String mediaMinioKey) {
        return dsl.insertInto(UserMedia.USER_MEDIA)
                .set(UserMedia.USER_MEDIA.ID, id)
                .set(UserMedia.USER_MEDIA.USER_ID, userId)
                .set(UserMedia.USER_MEDIA.MEDIA_MINIO_KEY, mediaMinioKey)
                .returning()
                .fetchOne();
    }
}
