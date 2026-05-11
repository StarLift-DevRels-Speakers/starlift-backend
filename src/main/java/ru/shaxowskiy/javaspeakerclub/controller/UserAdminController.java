package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.UserRoleUpdateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.UserRoleUpdateResponse;
import ru.shaxowskiy.javaspeakerclub.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @PatchMapping("/{userId}/role")
    public UserRoleUpdateResponse replaceUserRole(@PathVariable Long userId,
                                                  @RequestBody UserRoleUpdateRequest request) {
        if (request == null || request.role() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role is required");
        }
        var updatedUser = userService.replaceUserRole(userId, request.role());
        return new UserRoleUpdateResponse(updatedUser.getId(), updatedUser.getRoles());
    }
}
