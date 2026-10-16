package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final DSLContext dsl;

    public Optional<UsersRecord> findByUsername(String username) {
        return dsl.selectFrom(Users.USERS)
                .where(Users.USERS.USERNAME.eq(username))
                .fetchOptional();
    }

    public Optional<UsersRecord> findById(Long id) {
        return dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.eq(id))
                .fetchOptional();
    }

    public List<UsersRecord> findAll() {
        return dsl.selectFrom(Users.USERS)
                .orderBy(Users.USERS.ID.asc())
                .fetch();
    }

    public List<UsersRecord> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        return dsl.selectFrom(Users.USERS)
                .where(Users.USERS.ID.in(ids))
                .fetch();
    }

    public List<String> findDistinctRegions() {
        return dsl.selectDistinct(Users.USERS.REGION)
                .from(Users.USERS)
                .where(Users.USERS.REGION.isNotNull().and(Users.USERS.REGION.ne("")))
                .orderBy(Users.USERS.REGION.asc())
                .fetch(Users.USERS.REGION);
    }

    public List<String> findDistinctCompanies() {
        return dsl.selectDistinct(Users.USERS.COMPANY)
                .from(Users.USERS)
                .where(Users.USERS.COMPANY.isNotNull().and(Users.USERS.COMPANY.ne("")))
                .orderBy(Users.USERS.COMPANY.asc())
                .fetch(Users.USERS.COMPANY);
    }

    public UsersRecord save(String username, String encodedPassword, String firstName, String lastName, String surName) {
        return dsl.insertInto(Users.USERS)
                .set(Users.USERS.USERNAME, username)
                .set(Users.USERS.PASSWORD, encodedPassword)
                .set(Users.USERS.FIRST_NAME, firstName)
                .set(Users.USERS.LAST_NAME, lastName)
                .set(Users.USERS.SUR_NAME, surName)
                .returning()
                .fetchOne();
    }

    public Optional<UsersRecord> updateNps(Long id, BigDecimal nps) {
        int updated = dsl.update(Users.USERS)
                .set(Users.USERS.NPS, nps)
                .where(Users.USERS.ID.eq(id))
                .execute();

        return updated > 0 ? findById(id) : Optional.empty();
    }

    public Optional<UsersRecord> patchProfile(
            Long id,
            String firstName,
            String lastName,
            String surName,
            String about,
            String company,
            String companyRole,
            String avatarMediaKey
    ) {
        int updated = dsl.update(Users.USERS)
                .set(Users.USERS.FIRST_NAME, firstName)
                .set(Users.USERS.LAST_NAME, lastName)
                .set(Users.USERS.SUR_NAME, surName)
                .set(Users.USERS.ABOUT, about)
                .set(Users.USERS.COMPANY, company)
                .set(Users.USERS.COMPANY_ROLE, companyRole)
                .set(Users.USERS.AVATAR_MEDIA_KEY, avatarMediaKey)
                .where(Users.USERS.ID.eq(id))
                .execute();
        return updated > 0 ? findById(id) : Optional.empty();
    }

    public Optional<UsersRecord> patchDashboardProfile(
            Long id,
            String region,
            String company,
            String influence,
            Integer profileCompletion,
            Boolean topConferenceCandidate,
            String avatarColor,
            LocalDateTime lastEvaluationDate
    ) {
        int updated = dsl.update(Users.USERS)
                .set(Users.USERS.REGION, region)
                .set(Users.USERS.COMPANY, company)
                .set(Users.USERS.INFLUENCE, influence)
                .set(Users.USERS.PROFILE_COMPLETION, profileCompletion)
                .set(Users.USERS.TOP_CONFERENCE_CANDIDATE, topConferenceCandidate)
                .set(Users.USERS.AVATAR_COLOR, avatarColor)
                .set(Users.USERS.LAST_EVALUATION_DATE, lastEvaluationDate)
                .where(Users.USERS.ID.eq(id))
                .execute();
        return updated > 0 ? findById(id) : Optional.empty();
    }
}