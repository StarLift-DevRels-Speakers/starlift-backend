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
        boolean truncated = false;
        for (Fragment fragment : candidates) {
            String text = fragment.text();
            if (text.length() > properties.maxFragmentChars()) {
                text = Texts.truncate(text, properties.maxFragmentChars());
                truncated = true;
            }
            if (total + text.length() > properties.maxMaterialChars()) {
                truncated = true;
                break;
            }
            fragments.add(new Fragment(fragment.id(), fragment.label(), text));
            total += text.length();
        }
        return new MaterialSnapshot(
                talk == null ? null : talk.getName(),
                fileName,
                List.copyOf(fragments),
                total,
                truncated,
                fileUnsupported ? fileName : null
        );
    }

    /**
     * Хеш содержимого: одинаковый материал под тот же профиль аудитории не проверяется повторно.
     */
    public String contentHash(MaterialSnapshot snapshot) {
        StringBuilder value = new StringBuilder();
        for (Fragment fragment : snapshot.fragments()) {
            value.append(fragment.id()).append('\u0000').append(fragment.text()).append('\u0001');
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
