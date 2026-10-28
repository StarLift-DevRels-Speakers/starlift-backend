package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.GamificationProfileRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Участники соревнования — пользователи с ролью SPEAKER.
 */
@Component
@RequiredArgsConstructor
public class SpeakerDirectory {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final GamificationProfileRepository profileRepository;

    public record Participant(UsersRecord user, League league) {
        public Long id() {
            return user.getId();
        }
    }

    public boolean isSpeaker(Long userId) {
        return userId != null && userRoleRepository.findRolesByUserId(userId).contains(AppRole.SPEAKER);
    }

    public UsersRecord requireSpeaker(Long speakerId) {
        UsersRecord user = userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));
        if (!isSpeaker(speakerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId);
        }
        return user;
    }

    public void requireCurrentUserIsSpeaker(Long userId) {
        if (!isSpeaker(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only speakers take part in the competition");
        }
    }

    public List<Participant> participants() {
        List<Long> ids = userRoleRepository.findUserIdsByRole(AppRole.SPEAKER);
        Map<Long, League> leagues = profileRepository.findLeagues(ids);
        return userRepository.findByIds(ids).stream()
                .sorted(Comparator.comparing(UsersRecord::getId))
                .map(user -> new Participant(user, leagues.getOrDefault(user.getId(), League.BRONZE)))
                .toList();
    }
}
