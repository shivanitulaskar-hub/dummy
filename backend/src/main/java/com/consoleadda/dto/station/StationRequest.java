package com.consoleadda.dto.station;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StationRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{2,20}$", message = "2-20 letters, digits or dashes, e.g. PS5-06")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String consoleLabel,
        @Size(max = 50) String tvLabel,
        Boolean active,
        @Min(0) @Max(9999) Integer displayOrder,
        @Size(max = 500) String notes
) {
}
