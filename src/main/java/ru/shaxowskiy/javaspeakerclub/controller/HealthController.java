package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class HealthController {

    @GetMapping("/health")
    public String health(){
        log.info("Health check requested");
        return "OK";
    }
}
