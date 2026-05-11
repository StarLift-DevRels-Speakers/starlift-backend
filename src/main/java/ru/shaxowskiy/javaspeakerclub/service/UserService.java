package ru.shaxowskiy.javaspeakerclub.service;

import ru.shaxowskiy.javaspeakerclub.entity.User;

import java.util.Optional;

public interface UserService {
    User registerUser(String username, String password);
    User authenticateUser(String username, String password);
    Optional<User> findById(Long id);
}