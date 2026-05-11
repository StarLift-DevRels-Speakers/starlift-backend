package ru.shaxowskiy.javaspeakerclub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.shaxowskiy.javaspeakerclub.controller.AuthRestController;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.service.UserService;

@SpringBootApplication
public class JavaSpeakerClubApplication {

    public static void main(String[] args) {
        SpringApplication.run(JavaSpeakerClubApplication.class, args);
    }

}