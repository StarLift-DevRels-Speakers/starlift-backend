package ru.shaxowskiy.javaspeakerclub.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.exception.HttpProblemDetailWriter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ProblemJsonAccessDeniedHandler implements AccessDeniedHandler {

    private final HttpProblemDetailWriter problemDetailWriter;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        problemDetailWriter.write(response, HttpStatus.FORBIDDEN, "Доступ запрещён", "Недостаточно прав");
    }
}
