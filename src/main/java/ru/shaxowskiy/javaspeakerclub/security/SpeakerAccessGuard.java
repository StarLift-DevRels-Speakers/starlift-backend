package ru.shaxowskiy.javaspeakerclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

/**
 * Доступ к данным спикера: ADMIN и DEVREL — к любому, SPEAKER — только к своим.
 */
@Component
@RequiredArgsConstructor
public class SpeakerAccessGuard {

    private final UserRepository userRepository;

    public void assertCanManageSpeaker(Long speakerId) {
        Authentication auth = requireAuthentication();

        if (hasRole(auth, AppRole.ADMIN) || hasRole(auth, AppRole.DEVREL)) {
            return;
        }
        if (!hasRole(auth, AppRole.SPEAKER)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }

        UsersRecord me = findCurrentUser(auth);
        if (!me.getId().equals(speakerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Speaker can only update own data");
        }
    }

    public Long currentUserId() {
        return findCurrentUser(requireAuthentication()).getId();
    }

    private Authentication requireAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return auth;
    }

    private UsersRecord findCurrentUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Current user not found"));
    }

    private static boolean hasRole(Authentication auth, AppRole role) {
        return auth.getAuthorities().stream()
                .anyMatch(granted -> role.asAuthority().equals(granted.getAuthority()));
    }
}
