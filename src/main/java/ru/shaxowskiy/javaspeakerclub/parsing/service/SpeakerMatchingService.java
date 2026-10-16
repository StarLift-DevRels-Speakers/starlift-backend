package ru.shaxowskiy.javaspeakerclub.parsing.service;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpeakerMatchingService {

    private final DSLContext dsl;

    public Optional<Long> matchSpeakerId(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return Optional.empty();
        }

        List<String> parts = Arrays.stream(fullName.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .map(part -> part.toLowerCase(Locale.ROOT))
                .toList();

        if (parts.isEmpty()) {
            return Optional.empty();
        }

        List<UsersRecord> speakers = dsl.selectDistinct(
                        Users.USERS.ID,
                        Users.USERS.USERNAME,
                        Users.USERS.FIRST_NAME,
                        Users.USERS.LAST_NAME,
                        Users.USERS.SUR_NAME,
                        Users.USERS.PASSWORD,
                        Users.USERS.NPS,
                        Users.USERS.AVATAR_MEDIA_KEY,
                        Users.USERS.CREATED_DATE,
                        Users.USERS.LAST_MODIFIED_DATE
                )
                .from(Users.USERS)
                .join(UserRoles.USER_ROLES)
                .on(UserRoles.USER_ROLES.USER_ID.eq(Users.USERS.ID))
                .where(UserRoles.USER_ROLES.ROLE.eq("SPEAKER"))
                .fetchInto(UsersRecord.class);

        return speakers.stream()
                .filter(user -> matchesName(user, parts))
                .map(UsersRecord::getId)
                .findFirst();
    }

    private boolean matchesName(UsersRecord user, List<String> parts) {
        String displayName = buildDisplayName(user).toLowerCase(Locale.ROOT);
        if (displayName.isBlank()) {
            return false;
        }
        return parts.stream().allMatch(displayName::contains);
    }

    private String buildDisplayName(UsersRecord user) {
        return Arrays.asList(user.getFirstName(), user.getLastName(), user.getSurName(), user.getUsername())
                .stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }
}
