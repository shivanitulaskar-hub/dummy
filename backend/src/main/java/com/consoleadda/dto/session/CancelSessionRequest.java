package com.consoleadda.dto.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelSessionRequest(@NotBlank @Size(max = 255) String reason) {
}
