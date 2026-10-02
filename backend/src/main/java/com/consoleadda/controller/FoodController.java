package com.consoleadda.controller;

import com.consoleadda.dto.food.FoodCategoryRequest;
import com.consoleadda.dto.food.FoodCategoryResponse;
import com.consoleadda.dto.food.FoodItemRequest;
import com.consoleadda.dto.food.FoodItemResponse;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.service.FoodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Menu. Everyone reads it (cost prices only for admins); only admins change it. */
@RestController
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @GetMapping("/api/food-items")
    public List<FoodItemResponse> items(@RequestParam(defaultValue = "false") boolean includeInactive,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return foodService.items(includeInactive, user.isAdmin());
    }

    @PostMapping("/api/food-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public FoodItemResponse createItem(@Valid @RequestBody FoodItemRequest request) {
        return foodService.createItem(request);
    }

    @PutMapping("/api/food-items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FoodItemResponse updateItem(@PathVariable Long id, @Valid @RequestBody FoodItemRequest request) {
        return foodService.updateItem(id, request);
    }

    @GetMapping("/api/food-categories")
    public List<FoodCategoryResponse> categories() {
        return foodService.categories();
    }

    @PostMapping("/api/food-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public FoodCategoryResponse createCategory(@Valid @RequestBody FoodCategoryRequest request) {
        return foodService.createCategory(request);
    }

    @PutMapping("/api/food-categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public FoodCategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody FoodCategoryRequest request) {
        return foodService.updateCategory(id, request);
    }
}
