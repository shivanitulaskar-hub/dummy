package com.consoleadda.controller;

import com.consoleadda.dto.game.GameRequest;
import com.consoleadda.dto.game.GameResponse;
import com.consoleadda.security.AuthenticatedUser;
import com.consoleadda.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Game master. Everyone sees discs and availability; purchase cost and ROI only admins; only admins edit. */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping
    public List<GameResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return gameService.list(includeInactive, user.isAdmin());
    }

    @GetMapping("/{id}")
    public GameResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return gameService.get(id, user.isAdmin());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public GameResponse create(@Valid @RequestBody GameRequest request) {
        return gameService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public GameResponse update(@PathVariable Long id, @Valid @RequestBody GameRequest request) {
        return gameService.update(id, request);
    }
}
