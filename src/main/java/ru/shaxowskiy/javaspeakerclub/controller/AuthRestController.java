package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.dto.AuthResponse;
import ru.shaxowskiy.javaspeakerclub.dto.AuthRequest;
import ru.shaxowskiy.javaspeakerclub.dto.AuthTokensResponse;
import ru.shaxowskiy.javaspeakerclub.dto.RefreshRequest;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;
import ru.shaxowskiy.javaspeakerclub.security.JwtTokenService;
import ru.shaxowskiy.javaspeakerclub.service.UserService;

import java.util.Arrays;
import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    @PostMapping("/sign-on")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            var registeredUser = userService.registerUser(request.username(), request.password());
            var issued = jwtTokenService.issueTokens(registeredUser);
            return ResponseEntity.status(HttpStatus.CREATED).body(toAuthTokensResponse(issued));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(badRequestProblem("Не удалось зарегистрироваться"));
        }
    }

    @PostMapping("/sign-in")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        var authenticatedUser = userService.authenticateUser(request.username(), request.password());
        if (authenticatedUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(unauthorizedProblem());
        }
        var issued = jwtTokenService.issueTokens(authenticatedUser);
        return ResponseEntity.ok(toAuthTokensResponse(issued));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody RefreshRequest request) {
        try {
            jwtTokenService.revokeRefresh(request.refreshToken());
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(unauthorizedProblem());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            var issued = jwtTokenService.refreshTokens(request.refreshToken());
            return ResponseEntity.ok(toAuthTokensResponse(issued));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(unauthorizedProblem());
        }
    }

    @GetMapping("/roles")
    public List<String> roles() {
        return Arrays.stream(AppRole.values())
                .map(Enum::name)
                .toList();
    }

    private static ProblemDetail unauthorizedProblem() {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Не авторизован");
        pd.setTitle("Не авторизован");
        return pd;
    }

    private static ProblemDetail badRequestProblem(String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Ошибка запроса");
        return pd;
    }

    private AuthTokensResponse toAuthTokensResponse(JwtTokenService.IssuedTokens issuedTokens) {
        var tokens = issuedTokens.tokens();
        var user = issuedTokens.user();
        long accessExpiresIn = Math.max(0, tokens.accessExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        long refreshExpiresIn = Math.max(0, tokens.refreshExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        return new AuthTokensResponse(
                tokens.accessToken(),
                accessExpiresIn,
                tokens.refreshToken(),
                refreshExpiresIn,
                new AuthResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRoles(),
                        user.getCreatedDate(),
                        user.getLastModifiedDate()
                )
        );
    }
}