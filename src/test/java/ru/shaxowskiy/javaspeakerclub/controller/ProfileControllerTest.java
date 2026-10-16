package ru.shaxowskiy.javaspeakerclub.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.shaxowskiy.javaspeakerclub.entity.User;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.service.UserService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private TalkRepository talkRepository;

    @Test
    void me_includesTrimmedUserRegionInCities() {
        ProfileController controller = new ProfileController(userService, talkRepository);
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setRegion("  Казань  ");

        var authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_SPEAKER"))
        );

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(talkRepository.findBySpeakerId(1L)).thenReturn(List.of());

        ProfileController.MeResponse response = controller.me(authentication);

        assertThat(response.cities()).containsExactly("Казань");
    }

    @Test
    void me_usesDefaultCityWhenUserRegionIsMissing() {
        ProfileController controller = new ProfileController(userService, talkRepository);
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setRegion("   ");

        var authentication = new UsernamePasswordAuthenticationToken(
                "alice",
                "pwd",
                List.of(new SimpleGrantedAuthority("ROLE_SPEAKER"))
        );

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(talkRepository.findBySpeakerId(1L)).thenReturn(List.of());

        ProfileController.MeResponse response = controller.me(authentication);

        assertThat(response.cities()).containsExactly("Не указан");
    }
}
