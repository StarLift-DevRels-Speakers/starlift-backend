package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerRolesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RoleRepository {

    private final DSLContext dsl;

    public SpeakerRolesRecord create(String name, Long speakerId) {
        return dsl.insertInto(SpeakerRoles.SPEAKER_ROLES)
                .set(SpeakerRoles.SPEAKER_ROLES.NAME, name)
                .set(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID, speakerId)
                .returning()
                .fetchOne();
    }

    public Optional<SpeakerRolesRecord> findById(Long id) {
        return dsl.selectFrom(SpeakerRoles.SPEAKER_ROLES)
                .where(SpeakerRoles.SPEAKER_ROLES.ID.eq(id))
                .fetchOptional();
    }

    public List<SpeakerRolesRecord> findBySpeakerId(Long speakerId) {
        return dsl.selectFrom(SpeakerRoles.SPEAKER_ROLES)
                .where(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID.eq(speakerId))
                .orderBy(SpeakerRoles.SPEAKER_ROLES.NAME.asc())
                .fetch();
    }

    public List<SpeakerRolesRecord> findAll() {
        return dsl.selectFrom(SpeakerRoles.SPEAKER_ROLES)
                .orderBy(SpeakerRoles.SPEAKER_ROLES.NAME.asc())
                .fetch();
    }

    public List<String> findDistinctRoleNames() {
        return dsl.selectDistinct(SpeakerRoles.SPEAKER_ROLES.NAME)
                .from(SpeakerRoles.SPEAKER_ROLES)
                .where(SpeakerRoles.SPEAKER_ROLES.NAME.isNotNull().and(SpeakerRoles.SPEAKER_ROLES.NAME.ne("")))
                .orderBy(SpeakerRoles.SPEAKER_ROLES.NAME.asc())
                .fetch(SpeakerRoles.SPEAKER_ROLES.NAME);
    }

    public List<SpeakerRolesRecord> findBySpeakerIds(List<Long> speakerIds) {
        if (speakerIds == null || speakerIds.isEmpty()) {
            return List.of();
        }

        return dsl.selectFrom(SpeakerRoles.SPEAKER_ROLES)
                .where(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID.in(speakerIds))
                .orderBy(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID.asc(), SpeakerRoles.SPEAKER_ROLES.NAME.asc())
                .fetch();
    }

    public Map<Long, List<String>> findRoleNamesGroupedBySpeakerIds(List<Long> speakerIds) {
        return findBySpeakerIds(speakerIds).stream()
                .collect(Collectors.groupingBy(
                        SpeakerRolesRecord::getSpeakerId,
                        Collectors.mapping(SpeakerRolesRecord::getName, Collectors.toList())
                ));
    }

    /**
     * Find speakers whose roles contain the given substring (case-insensitive).
     * Returns distinct user records for speakers that have at least one role
     * matching the partial name.
     */
    public List<UsersRecord> findSpeakersByRoleNameContaining(String roleNamePart) {
        return dsl.selectDistinct(
                        Users.USERS.ID,
                        Users.USERS.USERNAME,
                        Users.USERS.FIRST_NAME,
                        Users.USERS.LAST_NAME,
                        Users.USERS.SUR_NAME,
                        Users.USERS.PASSWORD,
                        Users.USERS.NPS,
                        Users.USERS.CREATED_DATE,
                        Users.USERS.LAST_MODIFIED_DATE
                )
                .from(Users.USERS)
                .join(SpeakerRoles.SPEAKER_ROLES)
                .on(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID.eq(Users.USERS.ID))
                .where(SpeakerRoles.SPEAKER_ROLES.NAME.likeIgnoreCase("%" + escapeLike(roleNamePart) + "%"))
                .orderBy(Users.USERS.USERNAME.asc())
                .fetchInto(UsersRecord.class);
    }

    /**
     * Find all speakers who have at least one role assigned, with their roles.
     */
    public List<UsersRecord> findAllSpeakersWithRoles() {
        return dsl.selectDistinct(
                        Users.USERS.ID,
                        Users.USERS.USERNAME,
                        Users.USERS.FIRST_NAME,
                        Users.USERS.LAST_NAME,
                        Users.USERS.SUR_NAME,
                        Users.USERS.PASSWORD,
                        Users.USERS.NPS,
                        Users.USERS.CREATED_DATE,
                        Users.USERS.LAST_MODIFIED_DATE
                )
                .from(Users.USERS)
                .join(SpeakerRoles.SPEAKER_ROLES)
                .on(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID.eq(Users.USERS.ID))
                .orderBy(Users.USERS.USERNAME.asc())
                .fetchInto(UsersRecord.class);
    }

    public boolean deleteById(Long id) {
        return dsl.deleteFrom(SpeakerRoles.SPEAKER_ROLES)
                .where(SpeakerRoles.SPEAKER_ROLES.ID.eq(id))
                .execute() > 0;
    }

    private String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
