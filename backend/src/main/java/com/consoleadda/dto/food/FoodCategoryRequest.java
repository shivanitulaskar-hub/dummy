package com.consoleadda.dto.food;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FoodCategoryRequest(@NotBlank @Size(max = 60) String name, @Min(0) @Max(9999) Integer displayOrder,
                                  Boolean active) {
}
