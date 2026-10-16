package ru.shaxowskiy.javaspeakerclub.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class User {

    private Long id;

    private String username;

    private String firstName;

    private String lastName;

    private String surName;

    private String about;

    private String company;

    private String companyRole;

    private String region;

    private String avatarUrl;

    private String password;

    private LocalDateTime createdDate;

    private LocalDateTime lastModifiedDate;

    private List<String> roles;

    private List<String> tags;
}