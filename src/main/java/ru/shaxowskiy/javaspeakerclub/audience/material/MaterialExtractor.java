package ru.shaxowskiy.javaspeakerclub.audience.material;

import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGraphicFrame;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Текст презентации или документа по фрагментам: PDF — по страницам, PPTX — по слайдам (вместе с заметками
 * спикера), DOCX и текст — частями по абзацам. Для слайдов и страниц считаются картинки и диаграммы;
 * слайд из одних изображений остаётся фрагментом, чтобы учесть его в наглядности. Старые .ppt и .doc не поддерживаются.
 */
@Component
@RequiredArgsConstructor
public class MaterialExtractor {

    private static final Set<String> SUPPORTED = Set.of("pdf", "pptx", "docx", "txt", "md");
    private static final int PART_CHARS = 1200;

    private final AudienceProperties properties;

    public boolean supports(String fileName) {
        return SUPPORTED.contains(extension(fileName));
    }

    public List<Fragment> extract(String fileName, InputStream in) throws IOException {
        return switch (extension(fileName)) {
            case "pdf" -> pdf(in);
            case "pptx" -> pptx(in);
            case "docx" -> docx(in);
            case "txt", "md" -> parts(splitParagraphs(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            default -> List.of();
        };
    }

    private List<Fragment> pdf(InputStream in) throws IOException {
        List<Fragment> fragments = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(in.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = Math.min(document.getNumberOfPages(), properties.maxPages());
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = Texts.normalizeWhitespace(stripper.getText(document));
                int images = pdfImages(document.getPage(page - 1));
                if (!text.isEmpty() || images > 0) {
                    fragments.add(new Fragment("page-" + page, "Страница " + page, text.isEmpty() ? Fragment.IMAGE_ONLY_TEXT : text, images));
                }
            }
        }
        return fragments;
    }

    /**
     * Растровые изображения страницы; векторные схемы PDFBox так не видит, поэтому оценка нижняя.
     */
    private static int pdfImages(PDPage page) {
        PDResources resources = page.getResources();
        if (resources == null) {
            return 0;
        }
        int images = 0;
        for (COSName name : resources.getXObjectNames()) {
            if (resources.isImageXObject(name)) {
                images++;
            }
        }
        return images;
    }

    private List<Fragment> pptx(InputStream in) throws IOException {
        List<Fragment> fragments = new ArrayList<>();
        try (XMLSlideShow show = new XMLSlideShow(in)) {
            List<XSLFSlide> slides = show.getSlides();
            for (int index = 0; index < Math.min(slides.size(), properties.maxPages()); index++) {
                XSLFSlide slide = slides.get(index);
                StringBuilder text = new StringBuilder();
                int images = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    appendShapeText(shape, text);
                    images += countVisuals(shape);
                }
                String notes = notesText(slide.getNotes());
                if (!notes.isEmpty()) {
                    text.append("\nЗаметки спикера: ").append(notes);
                }
                String normalized = Texts.normalizeWhitespace(text.toString());
                if (!normalized.isEmpty() || images > 0) {
                    int number = index + 1;
                    fragments.add(new Fragment("slide-" + number, "Слайд " + number,
                            normalized.isEmpty() ? Fragment.IMAGE_ONLY_TEXT : normalized, images));
                }
            }
        }
        return fragments;
    }

    /**
     * Картинки, диаграммы и SmartArt; таблицы и надписи наглядностью не считаем.
     */
    private static int countVisuals(XSLFShape shape) {
        if (shape instanceof XSLFGroupShape group) {
            int total = 0;
            for (XSLFShape child : group.getShapes()) {
                total += countVisuals(child);
            }
            return total;
        }
        if (shape instanceof XSLFPictureShape) {
            return 1;
        }
        return shape instanceof XSLFGraphicFrame && !(shape instanceof XSLFTable) ? 1 : 0;
    }

    private static void appendShapeText(XSLFShape shape, StringBuilder out) {
        if (shape instanceof XSLFGroupShape group) {
            for (XSLFShape child : group.getShapes()) {
                appendShapeText(child, out);
            }
        } else if (shape instanceof XSLFTable table) {
            for (XSLFTableRow row : table.getRows()) {
                List<String> cells = new ArrayList<>();
                for (XSLFTableCell cell : row.getCells()) {
                    String text = Texts.trimToNull(cell.getText());
                    if (text != null) {
                        cells.add(text);
                    }
                }
                if (!cells.isEmpty()) {
                    out.append(String.join(" | ", cells)).append('\n');
                }
            }
        } else if (shape instanceof XSLFTextShape textShape) {
            String text = Texts.trimToNull(textShape.getText());
            if (text != null) {
                out.append(text).append('\n');
            }
        }
    }

    /**
     * Текст заметок — только из основного поля: в остальных плейсхолдерах дата, номер слайда и колонтитулы.
     */
    private static String notesText(XSLFNotes notes) {
        if (notes == null) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (XSLFTextShape placeholder : notes.getPlaceholders()) {
            if (placeholder.getPlaceholder() != Placeholder.BODY) {
                continue;
            }
            for (XSLFTextParagraph paragraph : placeholder.getTextParagraphs()) {
                String text = Texts.trimToNull(paragraph.getText());
                if (text != null) {
                    lines.add(text);
                }
            }
        }
        return String.join(" ", lines);
    }

    private List<Fragment> docx(InputStream in) throws IOException {
        List<String> paragraphs = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(in)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = Texts.trimToNull(paragraph.getText());
                if (text != null) {
                    paragraphs.add(text);
                }
            }
        }
        return parts(paragraphs);
    }

    private static List<String> splitParagraphs(String text) {
        return Texts.normalizeWhitespace(text).lines()
                .map(Texts::trimToNull)
                .filter(line -> line != null)
                .toList();
    }

    /**
     * Абзацы склеиваются в части примерно по PART_CHARS символов, чтобы модель ссылалась на место в тексте.
     */
    private List<Fragment> parts(List<String> paragraphs) {
        List<Fragment> fragments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (!current.isEmpty() && current.length() + paragraph.length() > PART_CHARS) {
                addPart(fragments, current);
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(paragraph);
            if (fragments.size() >= properties.maxPages()) {
                break;
            }
        }
        if (!current.isEmpty() && fragments.size() < properties.maxPages()) {
            addPart(fragments, current);
        }
        return fragments;
    }

    private static void addPart(List<Fragment> fragments, StringBuilder text) {
        int number = fragments.size() + 1;
        fragments.add(new Fragment("part-" + number, "Фрагмент " + number, text.toString()));
    }

    static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }
}
