package ru.shaxowskiy.javaspeakerclub.audience.material;

import java.util.List;

/**
 * Материал спикера в том виде, в каком его видит модель.
 * truncated — часть текста не поместилась в лимит; unsupportedFile — имя файла, текст которого не извлечь.
 * visual — наглядность презентации; null, если слайдов или страниц нет.
 */
public record MaterialSnapshot(
        String talkName,
        String fileName,
        List<Fragment> fragments,
        int totalChars,
        boolean truncated,
        String unsupportedFile,
        VisualStats visual
) {

    public MaterialSnapshot(String talkName, String fileName, List<Fragment> fragments, int totalChars, boolean truncated,
                            String unsupportedFile) {
        this(talkName, fileName, fragments, totalChars, truncated, unsupportedFile, null);
    }

    /**
     * Считается сервером по файлу. textOnlySlides — слайды без картинок и диаграмм, wallOfTextSlides — слайды,
     * где слов больше порога; в списках подписи вида «Слайд 3».
     */
    public record VisualStats(
            int slides,
            int slidesWithImages,
            int imageOnlySlides,
            int averageWords,
            List<String> textOnlySlides,
            List<String> wallOfTextSlides
    ) {

        public double textOnlyShare() {
            return slides == 0 ? 0 : (double) textOnlySlides.size() / slides;
        }
    }
}
