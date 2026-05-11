package ru.shaxowskiy.javaspeakerclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.entity.User;
import ru.shaxowskiy.javaspeakerclub.service.UserService;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String TOKEN_TYPE = "token_type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties properties;
    private final RefreshTokenStore refreshTokenStore;
    private final UserService userService;

    public IssuedTokens issueTokens(User user) {
        Instant now = Instant.now();
        Instant accessExp = now.plus(properties.accessTtl());
        Instant refreshExp = now.plus(properties.refreshTtl());

        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        String accessToken = encodeToken(user, accessJti, accessExp, TYPE_ACCESS);
        String refreshToken = encodeToken(user, refreshJti, refreshExp, TYPE_REFRESH);

        refreshTokenStore.store(refreshJti, user.getId(), properties.refreshTtl());

        TokenPair tokens = new TokenPair(accessToken, accessExp, refreshToken, refreshExp);
        return new IssuedTokens(user, tokens);
    }

    public IssuedTokens refreshTokens(String refreshToken) {
        Jwt jwt = decode(refreshToken);
        validateType(jwt, TYPE_REFRESH);

        Long userId = parseUserId(jwt);
        String jti = jwt.getId();

        if (!refreshTokenStore.isActive(jti, userId)) {
            throw new JwtException("Refresh token is expired or revoked");
        }

        User user = userService.findById(userId)
                .orElseThrow(() -> new JwtException("User not found"));

        Instant now = Instant.now();
        Instant accessExp = now.plus(properties.accessTtl());
        Instant refreshExp = now.plus(properties.refreshTtl());

        String newAccessJti = UUID.randomUUID().toString();
        String newRefreshJti = UUID.randomUUID().toString();

        refreshTokenStore.replace(jti, newRefreshJti, userId, properties.refreshTtl());

        String accessToken = encodeToken(user, newAccessJti, accessExp, TYPE_ACCESS);
        String rotatedRefreshToken = encodeToken(user, newRefreshJti, refreshExp, TYPE_REFRESH);

        TokenPair tokens = new TokenPair(accessToken, accessExp, rotatedRefreshToken, refreshExp);
        return new IssuedTokens(user, tokens);
    }

    public void revokeRefresh(String refreshToken) {
        Jwt jwt = decode(refreshToken);
        validateType(jwt, TYPE_REFRESH);
        refreshTokenStore.delete(jwt.getId());
    }

    public Authentication toAuthentication(String accessToken) {
        Jwt jwt = decode(accessToken);
        validateType(jwt, TYPE_ACCESS);

        String username = jwt.getClaimAsString("username");
        List<String> roles = jwt.getClaimAsStringList("roles");
        List<SimpleGrantedAuthority> authorities = roles == null
                ? Collections.emptyList()
                : roles.stream().map(role -> "ROLE_" + role).map(SimpleGrantedAuthority::new).toList();

        var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        auth.setDetails(jwt);
        return auth;
    }

    private String encodeToken(User user, String jti, Instant expiresAt, String tokenType) {
        List<String> roles = user.getRoles() == null ? List.of() : user.getRoles();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(Instant.now())
                .expiresAt(expiresAt)
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .claim("username", user.getUsername())
                .claim("roles", roles)
                .claim(TOKEN_TYPE, tokenType)
                .build();

        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private Jwt decode(String token) {
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException ex) {
            throw new JwtException("Invalid JWT: " + ex.getMessage(), ex);
        }
    }

    private void validateType(Jwt jwt, String expectedType) {
        String actual = jwt.getClaimAsString(TOKEN_TYPE);
        if (!expectedType.equals(actual)) {
            throw new JwtException("Unexpected token type");
        }
    }

    private Long parseUserId(Jwt jwt) {
        try {
            return Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            throw new JwtException("Invalid subject");
        }
    }

    public record TokenPair(
            String accessToken,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt
    ) {
    }

    public record IssuedTokens(
            User user,
            TokenPair tokens
    ) {
    }
}
