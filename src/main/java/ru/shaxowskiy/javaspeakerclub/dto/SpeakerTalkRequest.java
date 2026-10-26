package ru.shaxowskiy.javaspeakerclub.dto;

/**
 * Заготовка доклада: название, тема и описание без привязки к мероприятию.
 */
public record SpeakerTalkRequest(
        String name,
        String topic,
        String description
) {}
