package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserProfileTags;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserProfileTagRepository {

    private final DSLContext dsl;

    public List<String> findByUserId(Long userId) {
        return dsl.select(UserProfileTags.USER_PROFILE_TAGS.TAG)
                .from(UserProfileTags.USER_PROFILE_TAGS)
                .where(UserProfileTags.USER_PROFILE_TAGS.USER_ID.eq(userId))
                .orderBy(UserProfileTags.USER_PROFILE_TAGS.TAG.asc())
                .fetch(UserProfileTags.USER_PROFILE_TAGS.TAG);
    }

    public void replaceTags(Long userId, List<String> tags) {
        dsl.deleteFrom(UserProfileTags.USER_PROFILE_TAGS)
                .where(UserProfileTags.USER_PROFILE_TAGS.USER_ID.eq(userId))
                .execute();

        if (tags == null || tags.isEmpty()) {
            return;
        }

        tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .forEach(tag -> dsl.insertInto(UserProfileTags.USER_PROFILE_TAGS)
                        .set(UserProfileTags.USER_PROFILE_TAGS.USER_ID, userId)
                        .set(UserProfileTags.USER_PROFILE_TAGS.TAG, tag)
                        .execute());
    }
}
