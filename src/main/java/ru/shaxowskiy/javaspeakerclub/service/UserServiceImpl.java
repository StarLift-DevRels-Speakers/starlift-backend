package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.ProfilePatchRequest;
import ru.shaxowskiy.javaspeakerclub.entity.User;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(String username, String password, String firstName, String lastName, String surName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        String encodedPassword = passwordEncoder.encode(password);
        UsersRecord record = userRepository.save(username, encodedPassword, firstName, lastName, surName);
        userRoleRepository.assignRole(record.getId(), AppRole.USER);
        return mapToUser(record);
    }

    @Override
    public User authenticateUser(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(record -> passwordEncoder.matches(password, record.getPassword()))
                .map(this::mapToUser)
                .orElse(null);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToUser);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::mapToUser);
    }

    @Override
    @Transactional
    public User replaceUserRole(Long userId, AppRole role) {
        var userRecord = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        userRoleRepository.deleteByUserId(userId);
        userRoleRepository.assignRole(userId, role);
        return mapToUser(userRecord);
    }

    @Override
    @Transactional
    public User patchCurrentUser(ProfilePatchRequest request, Set<String> providedFields) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Long userId = userRepository.findByUsername(authentication.getName())
                .map(UsersRecord::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        return userRepository.patchProfile(
                        userId,
                        providedFields.contains("firstName"), request.firstName(),
                        providedFields.contains("lastName"), request.lastName(),
                        providedFields.contains("surName"), request.surName(),
                        providedFields.contains("avatarMediaKey"), request.avatarMediaKey()
                )
                .map(this::mapToUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private User mapToUser(UsersRecord record) {
        if (record == null) {
            return null;
        }
        User user = new User();
        user.setId(record.getId());
        user.setUsername(record.getUsername());
        user.setFirstName(record.getFirstName());
        user.setLastName(record.getLastName());
        user.setSurName(record.getSurName());
        user.setAvatarMediaKey(record.getAvatarMediaKey());
        user.setPassword(record.getPassword());
        // created_date / last_modified_date могут быть null, если БД проставляет значения по умолчанию
        if (record.getCreatedDate() != null) {
            user.setCreatedDate(record.getCreatedDate());
        } else {
            user.setCreatedDate(LocalDateTime.now());
        }
        if (record.getLastModifiedDate() != null) {
            user.setLastModifiedDate(record.getLastModifiedDate());
        } else {
            user.setLastModifiedDate(LocalDateTime.now());
        }
        user.setRoles(userRoleRepository.findRolesByUserId(record.getId())
                .stream()
                .map(Enum::name)
                .toList());
        return user;
    }
}