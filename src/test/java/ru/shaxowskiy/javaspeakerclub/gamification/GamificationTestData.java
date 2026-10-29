package ru.shaxowskiy.javaspeakerclub.gamification;

import org.jooq.DSLContext;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.PointReason;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.GamificationPointTransactions;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.GamificationSeasons;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerGamificationProfiles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.UserRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Данные для тестов соревновательного режима. Удаление пользователей каскадно чистит баллы, профили, итоги и значки.
 */
public final class GamificationTestData {

    public static final String ISOLATED_H2_URL =
            "spring.datasource.url=jdbc:h2:mem:gamification-${random.uuid};MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";

    private final DSLContext dsl;

    public GamificationTestData(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void clean() {
        dsl.deleteFrom(Users.USERS).execute();
        dsl.deleteFrom(GamificationSeasons.GAMIFICATION_SEASONS).execute();
    }

    public void user(long id, String username, String... roles) {
        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, id)
                .set(Users.USERS.USERNAME, username)
                .set(Users.USERS.PASSWORD, "pwd")
                .set(Users.USERS.FIRST_NAME, username)
                .execute();
        for (String role : roles) {
            dsl.insertInto(UserRoles.USER_ROLES)
                    .set(UserRoles.USER_ROLES.USER_ID, id)
                    .set(UserRoles.USER_ROLES.ROLE, role)
                    .execute();
        }
    }

    public void league(long speakerId, League league) {
        dsl.insertInto(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES)
                .set(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID, speakerId)
                .set(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.LEAGUE, league.name())
                .onConflict(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID)
                .doUpdate()
                .set(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.LEAGUE, league.name())
                .execute();
    }

    public void streak(long speakerId, int current, LocalDate lastActive) {
        league(speakerId, League.BRONZE);
        dsl.update(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES)
                .set(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.CURRENT_STREAK, current)
                .set(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.LONGEST_STREAK, current)
                .set(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.LAST_ACTIVE_DATE, lastActive)
                .where(SpeakerGamificationProfiles.SPEAKER_GAMIFICATION_PROFILES.SPEAKER_ID.eq(speakerId))
                .execute();
    }

    public long season(LocalDateTime startsAt, LocalDateTime endsAt) {
        return dsl.insertInto(GamificationSeasons.GAMIFICATION_SEASONS)
                .set(GamificationSeasons.GAMIFICATION_SEASONS.STARTS_AT, startsAt)
                .set(GamificationSeasons.GAMIFICATION_SEASONS.ENDS_AT, endsAt)
                .set(GamificationSeasons.GAMIFICATION_SEASONS.STATUS, "ACTIVE")
                .returning(GamificationSeasons.GAMIFICATION_SEASONS.ID)
                .fetchOne()
                .getId();
    }

    public void points(long speakerId, int points, LocalDateTime createdDate) {
        dsl.insertInto(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS)
                .set(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS.ID, UUID.randomUUID())
                .set(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS.SPEAKER_ID, speakerId)
                .set(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS.POINTS, points)
                .set(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS.REASON, PointReason.MANUAL.name())
                .set(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS.COMMENT, "test")
                .set(GamificationPointTransactions.GAMIFICATION_POINT_TRANSACTIONS.CREATED_DATE, createdDate)
                .execute();
    }
}
