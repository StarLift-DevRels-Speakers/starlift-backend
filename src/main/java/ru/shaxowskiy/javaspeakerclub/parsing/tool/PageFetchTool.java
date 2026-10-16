package ru.shaxowskiy.javaspeakerclub.parsing.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PageFetchTool {

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".pdf", ".jpg", ".jpeg", ".png", ".gif", ".zip", ".doc", ".docx"
    );

    private final WebClient parsingWebClient;
    private final ParsingProperties properties;

    public String fetchPageText(String url) {
        if (!isLikelyConferencePage(url)) {
            return null;
        }

        try {
            String html = parsingWebClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(properties.requestTimeout());

            if (html == null || html.isBlank()) {
                return null;
            }

            return toPlainText(html, url);
        } catch (Exception e) {
            log.warn("Failed to fetch url={}: {}", url, e.getMessage());
            return null;
        }
    }

    public String toPlainText(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);
        document.select("script, style, nav, footer, header, noscript").remove();
        String title = document.title();
        String body = document.body() != null ? document.body().text() : document.text();
        String combined = "Title: " + title + "\n\n" + body;
        int max = properties.maxHtmlChars();
        if (combined.length() > max) {
            combined = combined.substring(0, max);
        }
        return combined;
    }

    private boolean isLikelyConferencePage(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSIONS.stream().anyMatch(lower::endsWith)) {
            return false;
        }
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
