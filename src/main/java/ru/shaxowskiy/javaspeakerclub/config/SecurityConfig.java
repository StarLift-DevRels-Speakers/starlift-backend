package ru.shaxowskiy.javaspeakerclub.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.shaxowskiy.javaspeakerclub.security.AppUserDetailsService;
import ru.shaxowskiy.javaspeakerclub.security.JwtAuthenticationFilter;
import ru.shaxowskiy.javaspeakerclub.security.ProblemJsonAccessDeniedHandler;
import ru.shaxowskiy.javaspeakerclub.security.ProblemJsonAuthenticationEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AppUserDetailsService userDetailsService,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   ProblemJsonAuthenticationEntryPoint authenticationEntryPoint,
                                                   ProblemJsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
            .userDetailsService(userDetailsService)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/**/home", "/**/sign-on", "/**/sign-in", "/health", "/api/auth/**").permitAll()

                .requestMatchers(HttpMethod.GET,
                        "/api/talks/**",
                        "/api/lectures/**",
                        "/api/speakers/**",
                        "/api/media/download/**",
                        "/api/media/url/**",
                        "/api/reports/filters").hasAnyRole("USER", "SPEAKER", "DEVREL", "ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/talks/**", "/api/lectures/**").hasAnyRole("SPEAKER", "DEVREL", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/talks/**", "/api/lectures/**").hasAnyRole("SPEAKER", "DEVREL", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/talks/**", "/api/lectures/**").hasAnyRole("SPEAKER", "DEVREL", "ADMIN")

                .requestMatchers("/api/media/**").hasAnyRole("SPEAKER", "DEVREL", "ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/roles/**").hasAnyRole("USER", "SPEAKER", "DEVREL", "ADMIN")
                .requestMatchers("/api/roles/**").hasAnyRole("DEVREL", "ADMIN")

                .requestMatchers(HttpMethod.GET, "/api/reports/**").hasAnyRole("USER", "SPEAKER", "DEVREL", "ADMIN")
                .requestMatchers("/api/reports/**").hasAnyRole("DEVREL", "ADMIN")

                .requestMatchers(HttpMethod.PUT, "/api/speakers/**").hasAnyRole("DEVREL", "ADMIN")
                .requestMatchers("/api/speakers/**", "/api/me").hasAnyRole("USER", "SPEAKER", "DEVREL", "ADMIN")

                .anyRequest().hasRole("ADMIN")
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("*"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
