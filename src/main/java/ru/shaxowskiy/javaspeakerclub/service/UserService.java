package ru.shaxowskiy.javaspeakerclub.service;

import ru.shaxowskiy.javaspeakerclub.dto.ProfilePatchRequest;
import ru.shaxowskiy.javaspeakerclub.entity.User;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.util.Optional;

public interface UserService {
    User registerUser(String username, String password, String firstName, String lastName, String surName);
    User authenticateUser(String username, String password);
    Optional<User> findById(Long id);
    User replaceUserRole(Long userId, AppRole role);
    Optional<User> findByUsername(String username);
    User patchCurrentUser(ProfilePatchRequest request);
}