package ru.shaxowskiy.javaspeakerclub.security;

public enum AppRole {
    ADMIN,
    DEVREL,
    SPEAKER,
    USER;

    public String asAuthority() {
        return "ROLE_" + name();
    }
}
