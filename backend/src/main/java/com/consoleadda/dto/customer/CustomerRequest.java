package com.consoleadda.dto.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 20) String mobile,
        @Email @Size(max = 150) String email,
        @Size(max = 500) String notes
) {
}
