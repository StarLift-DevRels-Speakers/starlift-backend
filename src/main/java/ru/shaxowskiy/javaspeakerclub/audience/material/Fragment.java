package ru.shaxowskiy.javaspeakerclub.audience.material;

/**
 * Кусок материала, на который модель ссылается по id: slide-3, page-2, part-1, talk-description.
 * label — подпись для спикера: «Слайд 3». images — картинки и диаграммы на слайде или странице;
 * null, если у фрагмента нет визуальной части (карточка доклада, текст документа).
 */
public record Fragment(String id, String label, String text, Integer images) {

    /** Текст слайда, на котором нет ничего, кроме изображений. */
    public static final String IMAGE_ONLY_TEXT = "[на слайде только изображение]";

    public Fragment(String id, String label, String text) {
        this(id, label, text, null);
    }

    public boolean imageOnly() {
        return IMAGE_ONLY_TEXT.equals(text);
    }
}
