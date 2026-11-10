package ru.shaxowskiy.javaspeakerclub.audience.material;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialExtractorTest {

    private final MaterialExtractor extractor = new MaterialExtractor(AudienceTestData.properties());

    @Test
    void supportsTextFormatsOnly() {
        assertThat(extractor.supports("Slides.PPTX")).isTrue();
        assertThat(extractor.supports("talk.pdf")).isTrue();
        assertThat(extractor.supports("notes.docx")).isTrue();
        assertThat(extractor.supports("speech.md")).isTrue();
        assertThat(extractor.supports("old.ppt")).isFalse();
        assertThat(extractor.supports("video.mp4")).isFalse();
        assertThat(extractor.supports("noext")).isFalse();
        assertThat(extractor.supports(null)).isFalse();
    }

    @Test
    void extractsPdfByPagesSkippingEmpty() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addPage(document, "Intro to virtual threads");
            document.addPage(new PDPage());
            addPage(document, "Production case 40k RPS");
            document.save(out);
            pdf = out.toByteArray();
        }

        List<Fragment> fragments = extractor.extract("talk.pdf", new ByteArrayInputStream(pdf));

        assertThat(fragments).extracting(Fragment::id).containsExactly("page-1", "page-3");
        assertThat(fragments.get(0).label()).isEqualTo("Страница 1");
        assertThat(fragments.get(1).text()).contains("Production case 40k RPS");
    }

    @Test
    void extractsPptxBySlidesWithTablesAndNotes() throws IOException {
        byte[] pptx;
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide first = show.createSlide();
            XSLFTextBox title = first.createTextBox();
            title.setText("Что такое поток");
            XSLFTable table = first.createTable();
            XSLFTableRow row = table.addRow();
            row.addCell().setText("Платформенные");
            row.addCell().setText("Виртуальные");
            for (XSLFTextShape placeholder : show.getNotesSlide(first).getPlaceholders()) {
                if (placeholder.getPlaceholder() == Placeholder.BODY) {
                    placeholder.setText("Здесь рассказать про планировщик");
                }
            }

            show.createSlide();

            XSLFSlide third = show.createSlide();
            third.createTextBox().setText("Выводы");
            show.write(out);
            pptx = out.toByteArray();
        }

        List<Fragment> fragments = extractor.extract("slides.pptx", new ByteArrayInputStream(pptx));

        assertThat(fragments).extracting(Fragment::id).containsExactly("slide-1", "slide-3");
        assertThat(fragments.get(0).label()).isEqualTo("Слайд 1");
        assertThat(fragments.get(0).text())
                .contains("Что такое поток")
                .contains("Платформенные | Виртуальные")
                .contains("Заметки спикера: Здесь рассказать про планировщик");
        assertThat(fragments.get(1).text()).isEqualTo("Выводы");
    }

    @Test
    void extractsDocxIntoParts() throws IOException {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("а".repeat(900));
            document.createParagraph().createRun().setText("б".repeat(900));
            document.createParagraph();
            document.createParagraph().createRun().setText("в".repeat(100));
            document.write(out);
            docx = out.toByteArray();
        }

        List<Fragment> fragments = extractor.extract("speech.docx", new ByteArrayInputStream(docx));

        assertThat(fragments).extracting(Fragment::id).containsExactly("part-1", "part-2");
        assertThat(fragments.get(0).text()).isEqualTo("а".repeat(900));
        assertThat(fragments.get(1).text()).isEqualTo("б".repeat(900) + "\n" + "в".repeat(100));
        assertThat(fragments.get(1).label()).isEqualTo("Фрагмент 2");
    }

    @Test
    void extractsPlainText() throws IOException {
        String text = "Первый абзац выступления.\n\n\n   Второй   абзац.  \n";

        List<Fragment> fragments = extractor.extract("speech.txt", new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));

        assertThat(fragments).singleElement().extracting(Fragment::text)
                .isEqualTo("Первый абзац выступления.\nВторой абзац.");
    }

    @Test
    void unsupportedFormatGivesNothing() throws IOException {
        assertThat(extractor.extract("video.mp4", new ByteArrayInputStream(new byte[]{1, 2, 3}))).isEmpty();
    }

    private static void addPage(PDDocument document, String text) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
            content.newLineAtOffset(72, 700);
            content.showText(text);
            content.endText();
        }
    }

    @Test
    void countsPicturesAndKeepsImageOnlySlides() throws IOException {
        byte[] pptx;
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFPictureData picture = show.addPicture(png(), PictureData.PictureType.PNG);
            XSLFSlide withPicture = show.createSlide();
            withPicture.createTextBox().setText("Архитектура сервиса");
            withPicture.createPicture(picture);
            XSLFSlide imageOnly = show.createSlide();
            imageOnly.createPicture(picture);
            imageOnly.createPicture(picture);
            show.createSlide().createTextBox().setText("Только текст");
            show.write(out);
            pptx = out.toByteArray();
        }

        List<Fragment> fragments = extractor.extract("deck.pptx", new ByteArrayInputStream(pptx));

        assertThat(fragments).extracting(Fragment::id, Fragment::images).containsExactly(
                org.assertj.core.groups.Tuple.tuple("slide-1", 1),
                org.assertj.core.groups.Tuple.tuple("slide-2", 2),
                org.assertj.core.groups.Tuple.tuple("slide-3", 0));
        assertThat(fragments.get(1).imageOnly()).isTrue();
    }

    @Test
    void countsPdfImages() throws IOException {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDImageXObject image = PDImageXObject.createFromByteArray(document, png(), "dot.png");
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 50, 50, 100, 100);
            }
            addPage(document, "Text only page");
            document.save(out);
            pdf = out.toByteArray();
        }

        List<Fragment> fragments = extractor.extract("talk.pdf", new ByteArrayInputStream(pdf));

        assertThat(fragments).extracting(Fragment::id, Fragment::images).containsExactly(
                org.assertj.core.groups.Tuple.tuple("page-1", 1),
                org.assertj.core.groups.Tuple.tuple("page-2", 0));
        assertThat(fragments.getFirst().imageOnly()).isTrue();
    }

    @Test
    void documentsHaveNoVisualPart() throws IOException {
        List<Fragment> fragments = extractor.extract("speech.txt", new ByteArrayInputStream("Текст".getBytes(StandardCharsets.UTF_8)));

        assertThat(fragments).singleElement().extracting(Fragment::images).isNull();
    }

    private static byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
