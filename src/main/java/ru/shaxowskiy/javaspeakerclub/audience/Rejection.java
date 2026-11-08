package ru.shaxowskiy.javaspeakerclub.audience;

/**
 * Что валидатор отбросил или исправил в ответе модели и почему.
 */
public record Rejection(String item, String reason) {
}
