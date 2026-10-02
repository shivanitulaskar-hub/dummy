package com.consoleadda.dto.closing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReopenDayRequest(@NotBlank @Size(max = 255) String reason) {
}
