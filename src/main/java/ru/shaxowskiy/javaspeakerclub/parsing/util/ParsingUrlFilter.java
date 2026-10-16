package ru.shaxowskiy.javaspeakerclub.parsing.util;

import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ParsingUrlFilter {

    private static final Set<String> DEFAULT_BLOCKED_DOMAINS = Set.of(
            "all-events.ru",
            "workspace.ru",
            "ict2go.ru",
            "networkly.app",
            "timepad.ru",
            "afisha.ru",
            "leader-id.ru",
            "gomeetup.ru",
            "eventbrite.com",
            "meetup.com"
    );

    private static final List<String> BLOCKED_PATH_FRAGMENTS = List.of(
            "/calendar/",
            "/events/calendar",
            "/events/konferenciya"
    );

    private static final List<String> HIGH_PRIORITY_PATH_FRAGMENTS = List.of(
            "/speakers",
            "/program",
            "/schedule",
            "/talks",
            "/docs"
    );

    private static final List<String> KNOWN_CONFERENCE_DOMAIN_FRAGMENTS = List.of(
            "jpoint",
            "heisenbug",
            "dotnext",
            "jug.ru",
            "cppconf",
            "cpp.ru",
            "devoops",
            "highload",
            "teamlead",
            "piterpy",
            "mobius",
            "dumpconf"
    );

    private final Set<String> blockedDomains;

    public ParsingUrlFilter(ParsingProperties properties) {
        this.blockedDomains = mergeBlockedDomains(properties.blockedDomains());
    }

    public boolean isProcessable(String url) {
        return score(url) >= 0;
    }

    public int score(String url) {
        if (url == null || url.isBlank()) {
            return -100;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        String host = extractHost(lower);
        if (host == null) {
            return -100;
        }

        if (isBlockedDomain(host)) {
            return -20;
        }

        for (String fragment : BLOCKED_PATH_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return -20;
            }
        }

        int result = 0;
        for (String fragment : HIGH_PRIORITY_PATH_FRAGMENTS) {
            if (lower.contains(fragment)) {
                result += 10;
            }
        }
        for (String fragment : KNOWN_CONFERENCE_DOMAIN_FRAGMENTS) {
            if (host.contains(fragment)) {
                result += 5;
            }
        }
        return result;
    }

    public List<String> prioritize(List<String> urls) {
        return urls.stream()
                .filter(this::isProcessable)
                .sorted(Comparator.comparingInt(this::score).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isBlockedDomain(String host) {
        for (String blocked : blockedDomains) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) {
                return true;
            }
        }
        return false;
    }

    private static String extractHost(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }
            if (host.startsWith("www.")) {
                return host.substring(4);
            }
            return host;
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> mergeBlockedDomains(List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return DEFAULT_BLOCKED_DOMAINS;
        }
        var merged = new java.util.HashSet<>(DEFAULT_BLOCKED_DOMAINS);
        configured.stream()
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isBlank())
                .forEach(merged::add);
        return Set.copyOf(merged);
    }
}
