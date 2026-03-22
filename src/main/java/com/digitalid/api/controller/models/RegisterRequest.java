package com.digitalid.api.controller.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    private String username;
    private String name;
    private String email;
    private Long phoneNo;
    private LocalDate dateOfBirth;
    private Gender gender;
    private Role role;
    private String password;
    private Boolean termsAccepted;
}
