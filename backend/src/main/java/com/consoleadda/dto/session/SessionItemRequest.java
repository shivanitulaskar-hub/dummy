package com.consoleadda.dto.session;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SessionItemRequest(@NotNull Long foodItemId, @NotNull @Min(1) @Max(100) Integer quantity) {
}
