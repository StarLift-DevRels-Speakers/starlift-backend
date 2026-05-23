package ru.shaxowskiy.javaspeakerclub.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.dto.ProfilePatchRequest;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserProfileTags;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceImplTest {

    @Autowired
    private UserService userService;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        dsl.deleteFrom(UserProfileTags.USER_PROFILE_TAGS).execute();
        dsl.deleteFrom(UserRoles.USER_ROLES).execute();
        dsl.deleteFrom(Users.USERS).execute();

        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, 1L)
                .set(Users.USERS.USERNAME, "alice")
                .set(Users.USERS.PASSWORD, "pwd")
                .set(Users.USERS.FIRST_NAME, "Alice")
                .set(Users.USERS.LAST_NAME, "Doe")
                .set(Users.USERS.SUR_NAME, "A.")
                .set(Users.USERS.ABOUT, "old about")
                .set(Users.USERS.COMPANY, "OldCo")
                .set(Users.USERS.COMPANY_ROLE, "Developer")
                .execute();

        dsl.insertInto(UserRoles.USER_ROLES)
                .set(UserRoles.USER_ROLES.USER_ID, 1L)
                .set(UserRoles.USER_ROLES.ROLE, "SPEAKER")
                .execute();

        dsl.insertInto(UserProfileTags.USER_PROFILE_TAGS)
                .set(UserProfileTags.USER_PROFILE_TAGS.USER_ID, 1L)
                .set(UserProfileTags.USER_PROFILE_TAGS.TAG, "legacy")
                .execute();
    }

    @Test
    @WithMockUser(username = "alice", roles = {"SPEAKER"})
    void patchCurrentUserUpdatesExtendedFieldsAndReplacesTags() {
        var patched = userService.patchCurrentUser(new ProfilePatchRequest(
                "Alice Updated",
                null,
                null,
                "new about",
                "NewCo",
                "Tech Lead",
                List.of("java", " spring ", "java", ""),
                null
        ));

        assertThat(patched.getFirstName()).isEqualTo("Alice Updated");
        assertThat(patched.getLastName()).isEqualTo("Doe");
        assertThat(patched.getAbout()).isEqualTo("new about");
        assertThat(patched.getCompany()).isEqualTo("NewCo");
        assertThat(patched.getCompanyRole()).isEqualTo("Tech Lead");
        assertThat(patched.getTags()).containsExactly("java", "spring");

        var dbTags = dsl.select(UserProfileTags.USER_PROFILE_TAGS.TAG)
                .from(UserProfileTags.USER_PROFILE_TAGS)
                .where(UserProfileTags.USER_PROFILE_TAGS.USER_ID.eq(1L))
                .orderBy(UserProfileTags.USER_PROFILE_TAGS.TAG.asc())
                .fetch(UserProfileTags.USER_PROFILE_TAGS.TAG);
        assertThat(dbTags).containsExactly("java", "spring");
    }

    @Test
    @WithMockUser(username = "alice", roles = {"SPEAKER"})
    void patchCurrentUserKeepsTagsWhenTagsAreNull() {
        var patched = userService.patchCurrentUser(new ProfilePatchRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(patched.getTags()).containsExactly("legacy");
    }
}
