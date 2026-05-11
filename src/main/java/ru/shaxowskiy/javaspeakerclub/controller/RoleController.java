package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.shaxowskiy.javaspeakerclub.dto.NpsUpdateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.RoleCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.RoleResponse;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerResponse;
import ru.shaxowskiy.javaspeakerclub.service.RoleService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse create(@RequestBody RoleCreateRequest request) {
        return roleService.create(request);
    }

    @GetMapping("/roles")
    public List<RoleResponse> findAll() {
        return roleService.findAll();
    }

    @GetMapping("/roles/by-speaker/{speakerId}")
    public List<RoleResponse> findBySpeakerId(@PathVariable Long speakerId) {
        return roleService.findBySpeakerId(speakerId);
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/speakers")
    public List<SpeakerResponse> findSpeakersByRole(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) BigDecimal npsMin,
            @RequestParam(required = false) BigDecimal npsMax) {
        return roleService.findSpeakersByRole(role, npsMin, npsMax);
    }

    @GetMapping("/speakers/{id}")
    public SpeakerResponse findSpeakerById(@PathVariable Long id) {
        return roleService.findSpeakerById(id);
    }

    @PutMapping("/speakers/{id}/nps")
    public SpeakerResponse updateNps(@PathVariable Long id, @RequestBody NpsUpdateRequest request) {
        return roleService.updateNps(id, request.nps());
    }
}
