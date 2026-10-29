package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserMedia;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UserMediaRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserMediaRepository {

    private final DSLContext dsl;

    public UserMediaRecord create(UUID id, Long userId, String mediaMinioKey) {
        return create(id, userId, mediaMinioKey, null, null, null);
    }

    public UserMediaRecord create(UUID id, Long userId, String mediaMinioKey,
                                  String originalName, Long sizeBytes, UUID speakerTalkId) {
        return dsl.insertInto(UserMedia.USER_MEDIA)
                .set(UserMedia.USER_MEDIA.ID, id)
                .set(UserMedia.USER_MEDIA.USER_ID, userId)
                .set(UserMedia.USER_MEDIA.MEDIA_MINIO_KEY, mediaMinioKey)
                .set(UserMedia.USER_MEDIA.ORIGINAL_NAME, originalName)
                .set(UserMedia.USER_MEDIA.SIZE_BYTES, sizeBytes)
                .set(UserMedia.USER_MEDIA.SPEAKER_TALK_ID, speakerTalkId)
                .returning()
                .fetchOne();
    }

    public Optional<UserMediaRecord> findByUserIdAndMediaMinioKey(Long userId, String mediaMinioKey) {
        return dsl.selectFrom(UserMedia.USER_MEDIA)
                .where(UserMedia.USER_MEDIA.USER_ID.eq(userId))
                .and(UserMedia.USER_MEDIA.MEDIA_MINIO_KEY.eq(mediaMinioKey))
                .fetchOptional();
    }

    public List<UserMediaRecord> findByUserId(Long userId) {
        return dsl.selectFrom(UserMedia.USER_MEDIA)
                .where(UserMedia.USER_MEDIA.USER_ID.eq(userId))
                .orderBy(UserMedia.USER_MEDIA.CREATED_DATE.desc())
                .fetch();
    }

    public Optional<UserMediaRecord> findByIdAndUserId(UUID id, Long userId) {
        return dsl.selectFrom(UserMedia.USER_MEDIA)
                .where(UserMedia.USER_MEDIA.ID.eq(id))
                .and(UserMedia.USER_MEDIA.USER_ID.eq(userId))
                .fetchOptional();
    }

    public void deleteById(UUID id) {
        dsl.deleteFrom(UserMedia.USER_MEDIA)
                .where(UserMedia.USER_MEDIA.ID.eq(id))
                .execute();
    }

    public boolean existsByUserIdAndMediaMinioKey(Long userId, String mediaMinioKey) {
        return dsl.fetchExists(
                dsl.selectFrom(UserMedia.USER_MEDIA)
                        .where(UserMedia.USER_MEDIA.USER_ID.eq(userId))
                        .and(UserMedia.USER_MEDIA.MEDIA_MINIO_KEY.eq(mediaMinioKey))
        );
    }
}
