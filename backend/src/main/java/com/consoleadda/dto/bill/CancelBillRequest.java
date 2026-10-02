package com.consoleadda.dto.bill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelBillRequest(@NotBlank @Size(max = 255) String reason) {
}
