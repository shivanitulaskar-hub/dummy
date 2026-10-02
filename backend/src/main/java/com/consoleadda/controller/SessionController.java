package com.consoleadda.controller;

import com.consoleadda.dto.session.CancelSessionRequest;
import com.consoleadda.dto.session.CompleteSessionRequest;
import com.consoleadda.dto.session.SessionResponse;
import com.consoleadda.dto.session.StartSessionRequest;
import com.consoleadda.dto.session.UpdateSessionRequest;
import com.consoleadda.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse start(@Valid @RequestBody StartSessionRequest request) {
        return sessionService.start(request);
    }

    /** Active sessions with live estimates; polled by the Live Sessions screen. */
    @GetMapping("/active")
    public List<SessionResponse> active() {
        return sessionService.active();
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable Long id) {
        return sessionService.get(id);
    }

    @PatchMapping("/{id}")
    public SessionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSessionRequest request) {
        return sessionService.update(id, request);
    }

    /** END: calculates the bill as of now without saving anything. */
    @PostMapping("/{id}/end")
    public SessionResponse end(@PathVariable Long id) {
        return sessionService.preview(id);
    }

    /**
     * Finalises the bill: bill number, payment, station freed. Send an {@code Idempotency-Key} header (any
     * unique string per attempt) so a retried request returns the same bill instead of failing.
     */
    @PostMapping("/{id}/complete")
    public SessionResponse complete(@PathVariable Long id, @Valid @RequestBody CompleteSessionRequest request,
                                    @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 64)
                                    String idempotencyKey) {
        return sessionService.complete(id, request, idempotencyKey);
    }

    @PostMapping("/{id}/cancel")
    public SessionResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelSessionRequest request) {
        return sessionService.cancel(id, request);
    }
}
