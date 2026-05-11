package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.math.BigDecimal;
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
}