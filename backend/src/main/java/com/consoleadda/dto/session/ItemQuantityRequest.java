package com.consoleadda.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemQuantityRequest(@NotNull @Min(1) @Max(100) Integer quantity) {
}
