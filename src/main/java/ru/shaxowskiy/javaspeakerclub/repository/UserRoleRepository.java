package ru.shaxowskiy.javaspeakerclub.repository;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDateTime;
import java.util.List;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

@Repository
@RequiredArgsConstructor
public class UserRoleRepository {

    private static final Table<?> USER_ROLES = table(name("user_roles"));
    private static final Field<Long> USER_ID = field(name("user_id"), Long.class);
    private static final Field<String> ROLE = field(name("role"), String.class);
    private static final Field<LocalDateTime> CREATED_DATE = field(name("created_date"), LocalDateTime.class);

    private final DSLContext dsl;

    public List<AppRole> findRolesByUserId(Long userId) {
        return dsl.select(ROLE)
                .from(USER_ROLES)
                .where(USER_ID.eq(userId))
                .fetch(ROLE)
                .stream()
                .map(AppRole::valueOf)
                .toList();
    }

    public void assignRole(Long userId, AppRole role) {
        dsl.insertInto(USER_ROLES)
                .set(USER_ID, userId)
                .set(ROLE, role.name())
                .set(CREATED_DATE, LocalDateTime.now())
                .onConflict(USER_ID, ROLE)
                .doNothing()
                .execute();
    }
}
