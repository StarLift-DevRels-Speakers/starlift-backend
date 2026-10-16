package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

/** Кто выполняет действие: администратор видит все мероприятия, организатор — только свои. */
public record ProducerActor(Long userId, boolean admin) {
}
