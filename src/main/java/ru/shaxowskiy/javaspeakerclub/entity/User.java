package ru.shaxowskiy.javaspeakerclub.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class User {

    private Long id;

    private String username;

    private String password;

    private LocalDateTime createdDate;

    private LocalDateTime lastModifiedDate;

    private List<String> roles;
}