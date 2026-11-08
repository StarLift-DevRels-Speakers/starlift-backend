package ru.shaxowskiy.javaspeakerclub.audience.material;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Собирает материал для проверки: карточка доклада (название, тема, описание) и текст файла.
 * Длинные фрагменты обрезаются, а всё, что не влезло в общий лимит, отбрасывается с флагом truncated.
 */
@Component
@RequiredArgsConstructor
public class MaterialAssembler {

    /** Больше стольких слов на слайде — «стена текста», которую из зала не прочитать. */
    static final int WALL_OF_TEXT_WORDS = 80;

    private final AudienceProperties properties;

    public MaterialSnapshot assemble(SpeakerTalksRecord talk, String fileName, List<Fragment> fileFragments, boolean fileUnsupported) {
        List<Fragment> candidates = new ArrayList<>();
        if (talk != null) {
            add(candidates, "talk-name", "Название доклада", talk.getName());
            add(candidates, "talk-topic", "Тема доклада", talk.getTopic());
            add(candidates, "talk-description", "Описание доклада", talk.getDescription());
        }
        if (fileFragments != null) {
            candidates.addAll(fileFragments);
        }

        List<Fragment> fragments = new ArrayList<>();
        int total = 0;
        int used = 0;
        boolean truncated = false;
        for (Fragment fragment : candidates) {
            String text = fragment.text();
            if (text.length() > properties.maxFragmentChars()) {
                text = Texts.truncate(text, properties.maxFragmentChars());
                truncated = true;
            }
            if (used + text.length() > properties.maxMaterialChars()) {
                truncated = true;
                break;
            }
            fragments.add(new Fragment(fragment.id(), fragment.label(), text, fragment.images()));
            used += text.length();
            // Слайд из одних картинок не добавляет материала для оценки содержания.
            if (!fragment.imageOnly()) {
                total += text.length();
            }
        }
        return new MaterialSnapshot(
                talk == null ? null : talk.getName(),
                fileName,
                List.copyOf(fragments),
                total,
                truncated,
                fileUnsupported ? fileName : null,
                visual(fileFragments)
        );
    }

    /**
     * Наглядность по всем слайдам файла, а не только по тем, что поместились в лимит текста.
     */
    static MaterialSnapshot.VisualStats visual(List<Fragment> fileFragments) {
        List<Fragment> slides = fileFragments == null ? List.of()
                : fileFragments.stream().filter(fragment -> fragment.images() != null).toList();
        if (slides.isEmpty()) {
            return null;
        }
        int withImages = 0;
        int imageOnly = 0;
        int words = 0;
        List<String> textOnly = new ArrayList<>();
        List<String> wallOfText = new ArrayList<>();
        for (Fragment slide : slides) {
            if (slide.images() > 0) {
                withImages++;
            } else {
                textOnly.add(slide.label());
            }
            if (slide.imageOnly()) {
                imageOnly++;
                continue;
            }
            int count = words(slide.text());
            words += count;
            if (count > WALL_OF_TEXT_WORDS) {
                wallOfText.add(slide.label());
            }
        }
        int textSlides = slides.size() - imageOnly;
        return new MaterialSnapshot.VisualStats(
                slides.size(),
                withImages,
                imageOnly,
                textSlides == 0 ? 0 : Math.round((float) words / textSlides),
                List.copyOf(textOnly),
                List.copyOf(wallOfText)
        );
    }

    static int words(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    /**
     * Хеш содержимого вместе с числом картинок: одинаковый материал под тот же профиль аудитории не проверяется повторно.
     */
    public String contentHash(MaterialSnapshot snapshot) {
        StringBuilder value = new StringBuilder();
        for (Fragment fragment : snapshot.fragments()) {
            value.append(fragment.id()).append('\u0000').append(fragment.text()).append('\u0000').append(fragment.images()).append('\u0001');
        }
        return Texts.sha256(value.toString());
    }

    private static void add(List<Fragment> fragments, String id, String label, String text) {
        String normalized = Texts.trimToNull(Texts.normalizeWhitespace(text));
        if (normalized != null) {
            fragments.add(new Fragment(id, label, normalized));
        }
    }
}
