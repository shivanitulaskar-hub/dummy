package com.consoleadda.controller;

import com.consoleadda.dto.session.CancelSessionRequest;
import com.consoleadda.dto.session.CompleteSessionRequest;
import com.consoleadda.dto.session.EndPreviewRequest;
import com.consoleadda.dto.session.ItemQuantityRequest;
import com.consoleadda.dto.session.SessionItemRequest;
import com.consoleadda.dto.session.SessionResponse;
import com.consoleadda.dto.session.StartSessionRequest;
import com.consoleadda.dto.session.UpdateSessionRequest;
import com.consoleadda.service.SessionItemService;
import com.consoleadda.service.SessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final SessionItemService sessionItemService;

    public SessionController(SessionService sessionService, SessionItemService sessionItemService) {
        this.sessionService = sessionService;
        this.sessionItemService = sessionItemService;
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
    public SessionResponse end(@PathVariable Long id, @RequestBody(required = false) EndPreviewRequest request) {
        return sessionService.preview(id, request == null ? null : request.discountId(),
                request == null ? null : request.endedAt());
    }

    /** Add food/drinks to a running session (same item added again increases the quantity). */
    @PostMapping("/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse addItem(@PathVariable Long id, @Valid @RequestBody SessionItemRequest request) {
        return sessionItemService.addItem(id, request);
    }

    @PatchMapping("/{id}/items/{lineId}")
    public SessionResponse changeItemQuantity(@PathVariable Long id, @PathVariable Long lineId,
                                              @Valid @RequestBody ItemQuantityRequest request) {
        return sessionItemService.changeQuantity(id, lineId, request.quantity());
    }

    @DeleteMapping("/{id}/items/{lineId}")
    public SessionResponse removeItem(@PathVariable Long id, @PathVariable Long lineId) {
        return sessionItemService.removeItem(id, lineId);
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
