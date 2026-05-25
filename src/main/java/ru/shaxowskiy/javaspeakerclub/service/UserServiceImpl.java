package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import ru.shaxowskiy.javaspeakerclub.repository.UserMediaRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserProfileTagRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserProfileTagRepository userProfileTagRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMediaRepository userMediaRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;

    @Override
    public User registerUser(String username, String password, String firstName, String lastName, String surName) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        String encodedPassword = passwordEncoder.encode(password);
        UsersRecord record = userRepository.save(username, encodedPassword, firstName, lastName, surName);
        userRoleRepository.assignRole(record.getId(), AppRole.SPEAKER);
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
    public User patchCurrentUser(ProfilePatchRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        UsersRecord currentUserRecord = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String mergedFirstName = request.firstName() != null ? request.firstName() : currentUserRecord.getFirstName();
        String mergedLastName = request.lastName() != null ? request.lastName() : currentUserRecord.getLastName();
        String mergedSurName = request.surName() != null ? request.surName() : currentUserRecord.getSurName();
        String mergedAbout = request.about() != null ? request.about() : currentUserRecord.getAbout();
        String mergedCompany = request.company() != null ? request.company() : currentUserRecord.getCompany();
        String mergedCompanyRole = request.companyRole() != null
                ? request.companyRole()
                : currentUserRecord.getCompanyRole();
        String mergedAvatarMediaKey = mergeAvatarMediaKey(currentUserRecord.getId(), request, currentUserRecord);

        User patchedUser = userRepository.patchProfile(
                        currentUserRecord.getId(),
                        mergedFirstName,
                        mergedLastName,
                        mergedSurName,
                        mergedAbout,
                        mergedCompany,
                        mergedCompanyRole,
                        mergedAvatarMediaKey
                )
                .map(this::mapToUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.tags() != null) {
            userProfileTagRepository.replaceTags(currentUserRecord.getId(), request.tags());
            patchedUser.setTags(userProfileTagRepository.findByUserId(currentUserRecord.getId()));
        }

        return patchedUser;
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
        user.setAbout(record.getAbout());
        user.setCompany(record.getCompany());
        user.setCompanyRole(record.getCompanyRole());
        user.setRegion(record.getRegion());
        user.setAvatarUrl(buildAvatarUrl(record.getAvatarMediaKey()));
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
        user.setTags(userProfileTagRepository.findByUserId(record.getId()));
        return user;
    }

    private String buildAvatarUrl(String avatarMediaKey) {
        if (avatarMediaKey == null || avatarMediaKey.isBlank()) {
            return null;
        }
        try {
            return minioService.getPresignedUrl(avatarMediaKey);
        } catch (RuntimeException ex) {
            log.warn("Failed to build avatar URL for key {}", avatarMediaKey, ex);
            return null;
        }
    }

    private String mergeAvatarMediaKey(Long currentUserId, ProfilePatchRequest request, UsersRecord currentUserRecord) {
        if (request.avatarMediaKey() == null) {
            return currentUserRecord.getAvatarMediaKey();
        }
        String requestedAvatarMediaKey = request.avatarMediaKey().trim();
        if (requestedAvatarMediaKey.isEmpty()) {
            return null;
        }
        if (!userMediaRepository.existsByUserIdAndMediaMinioKey(currentUserId, requestedAvatarMediaKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarMediaKey does not belong to current user");
        }
        return requestedAvatarMediaKey;
    }
}