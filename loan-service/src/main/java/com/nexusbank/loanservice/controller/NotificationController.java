package com.nexusbank.loanservice.controller;

import com.nexusbank.loanservice.service.SseEmitterManager;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint that browser clients subscribe to for real-time loan saga
 * notifications (disbursement completed / failed).
 *
 * The browser connects with:
 *   GET /api/loans/notifications/subscribe
 *
 * Authentication note: native EventSource does not support custom headers,
 * so the gateway's JwtAuthenticationFilter also accepts a ?token= query
 * parameter for this path. The X-User-Id header is injected by the gateway
 * as usual and is used here to scope the SSE stream to this user.
 */
@RestController
@RequestMapping("/api/loans/notifications")
public class NotificationController {

    private final SseEmitterManager sseEmitterManager;

    public NotificationController(SseEmitterManager sseEmitterManager) {
        this.sseEmitterManager = sseEmitterManager;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter subscribe(Authentication auth) {
        Long userId = parseUserId(auth);
        return sseEmitterManager.register(userId);
    }

    private Long parseUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        try {
            return Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
