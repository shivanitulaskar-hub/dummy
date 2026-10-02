package com.consoleadda.dto.rental;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRentalRequest(@NotBlank @Size(max = 255) String reason) {
}
