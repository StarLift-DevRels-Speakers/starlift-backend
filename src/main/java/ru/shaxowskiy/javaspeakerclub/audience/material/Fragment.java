package ru.shaxowskiy.javaspeakerclub.audience.material;

/**
 * Кусок материала, на который модель ссылается по id: slide-3, page-2, part-1, talk-description.
 * label — подпись для спикера: «Слайд 3».
 */
public record Fragment(String id, String label, String text) {
}
