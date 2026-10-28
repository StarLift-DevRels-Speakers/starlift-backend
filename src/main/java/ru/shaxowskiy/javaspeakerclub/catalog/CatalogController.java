package ru.shaxowskiy.javaspeakerclub.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.catalog.CatalogService.CatalogSpeaker;
import ru.shaxowskiy.javaspeakerclub.catalog.CatalogService.CatalogSpeakerDetails;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/speakers")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public List<CatalogSpeaker> search(@RequestParam(required = false) String query) {
        return catalogService.search(query);
    }

    @GetMapping("/{speakerId}")
    public CatalogSpeakerDetails details(@PathVariable Long speakerId) {
        return catalogService.details(speakerId);
    }
}
