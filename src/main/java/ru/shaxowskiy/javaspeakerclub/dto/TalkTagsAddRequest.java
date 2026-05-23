package ru.shaxowskiy.javaspeakerclub.dto;

import java.util.List;

public record TalkTagsAddRequest(
        List<String> tags
) {}
