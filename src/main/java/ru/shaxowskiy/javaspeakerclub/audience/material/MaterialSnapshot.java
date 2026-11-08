package ru.shaxowskiy.javaspeakerclub.audience.material;

import java.util.List;

/**
 * Материал спикера в том виде, в каком его видит модель.
 * truncated — часть текста не поместилась в лимит; unsupportedFile — имя файла, текст которого не извлечь.
 */
public record MaterialSnapshot(
        String talkName,
        String fileName,
        List<Fragment> fragments,
        int totalChars,
        boolean truncated,
        String unsupportedFile
) {
}
