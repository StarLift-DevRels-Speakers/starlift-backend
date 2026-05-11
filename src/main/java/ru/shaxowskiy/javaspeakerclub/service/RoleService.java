package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.RoleCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.RoleResponse;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerRolesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.RoleRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoleResponse create(RoleCreateRequest request) {
        userRepository.findById(request.speakerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Speaker not found: " + request.speakerId()));

        SpeakerRolesRecord record = roleRepository.create(request.name(), request.speakerId());
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findBySpeakerId(Long speakerId) {
        userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        return roleRepository.findBySpeakerId(speakerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpeakerResponse> findSpeakersByRole(String roleNamePart, BigDecimal npsMin, BigDecimal npsMax) {
        if (npsMin != null && npsMax != null && npsMin.compareTo(npsMax) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "npsMin must be <= npsMax");
        }
        if (npsMin != null && (npsMin.compareTo(BigDecimal.ZERO) < 0 || npsMin.compareTo(BigDecimal.TEN) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "npsMin must be between 0 and 10");
        }
        if (npsMax != null && (npsMax.compareTo(BigDecimal.ZERO) < 0 || npsMax.compareTo(BigDecimal.TEN) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "npsMax must be between 0 and 10");
        }

        List<UsersRecord> speakers = (roleNamePart == null || roleNamePart.isBlank())
                ? roleRepository.findAllSpeakersWithRoles()
                : roleRepository.findSpeakersByRoleNameContaining(roleNamePart);

        return speakers.stream()
                .filter(user -> {
                    BigDecimal nps = user.getNps();
                    if (nps == null) return npsMin == null && npsMax == null;
                    if (npsMin != null && nps.compareTo(npsMin) < 0) return false;
                    if (npsMax != null && nps.compareTo(npsMax) > 0) return false;
                    return true;
                })
                .map(this::toSpeakerResponse)
                .toList();
    }

    @Transactional
    public SpeakerResponse updateNps(Long id, BigDecimal nps) {
        if (nps != null && (nps.compareTo(BigDecimal.ZERO) < 0 || nps.compareTo(BigDecimal.TEN) > 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NPS must be between 0 and 10.0");
        }

        UsersRecord updated = userRepository.updateNps(id, nps)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Speaker not found: " + id));

        return toSpeakerResponse(updated);
    }

    @Transactional(readOnly = true)
    public SpeakerResponse findSpeakerById(Long id) {
        UsersRecord user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Speaker not found: " + id));

        return toSpeakerResponse(user);
    }

    private SpeakerResponse toSpeakerResponse(UsersRecord user) {
        List<RoleResponse> roles = roleRepository.findBySpeakerId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
        return new SpeakerResponse(
                user.getId(),
                user.getUsername(),
                user.getNps(),
                user.getCreatedDate(),
                roles
        );
    }

    @Transactional
    public void delete(Long id) {
        if (!roleRepository.deleteById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + id);
        }
    }

    private RoleResponse toResponse(SpeakerRolesRecord record) {
        return new RoleResponse(
                record.getId(),
                record.getName(),
                record.getSpeakerId(),
                record.getCreatedDate()
        );
    }
}
