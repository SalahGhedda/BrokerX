package com.brokerx.interfaces.rest.dto;

import java.time.LocalDate;

public record SignupRequest(
        String email,
        String phone,
        String password,
        String fullName,
        String addressLine,
        LocalDate dateOfBirth
) {
}
