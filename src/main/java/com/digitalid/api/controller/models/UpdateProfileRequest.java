package com.digitalid.api.controller.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    private String name;
    private LocalDate dateOfBirth;
    private Gender gender;
    private Boolean marketingOptIn;
}
