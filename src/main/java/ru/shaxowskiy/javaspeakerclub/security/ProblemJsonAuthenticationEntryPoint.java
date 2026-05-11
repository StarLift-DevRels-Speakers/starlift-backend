package ru.shaxowskiy.javaspeakerclub.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.exception.HttpProblemDetailWriter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ProblemJsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HttpProblemDetailWriter problemDetailWriter;

    @Override
    public void commence(HttpServletRequest request,
                           HttpServletResponse response,
                           AuthenticationException authException) throws IOException, ServletException {
        problemDetailWriter.write(response, HttpStatus.UNAUTHORIZED, "Не авторизован", "Не авторизован");
    }
}
