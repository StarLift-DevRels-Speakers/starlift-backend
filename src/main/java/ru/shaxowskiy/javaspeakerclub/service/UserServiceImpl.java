package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.entity.User;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("User already exists");
        }
        String encodedPassword = passwordEncoder.encode(password);
        UsersRecord record = userRepository.save(username, encodedPassword);
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

    private User mapToUser(UsersRecord record) {
        if (record == null) {
            return null;
        }
        User user = new User();
        user.setId(record.getId());
        user.setUsername(record.getUsername());
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