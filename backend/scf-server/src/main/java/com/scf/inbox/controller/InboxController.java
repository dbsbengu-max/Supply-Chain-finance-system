package com.scf.inbox.controller;

import com.scf.common.dto.ApiResponse;
import com.scf.inbox.dto.InboxDtos.InboxEventView;
import com.scf.inbox.dto.InboxDtos.InboxFeedView;
import com.scf.inbox.service.InboxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inbox")
public class InboxController {

    private final InboxService inboxService;

    public InboxController(InboxService inboxService) {
        this.inboxService = inboxService;
    }

    @GetMapping("/feed")
    public ApiResponse<InboxFeedView> feed(
            HttpServletRequest request,
            @RequestParam(required = false) String source,
            @RequestParam(name = "unread_only", required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(
                inboxService.feed(source, unreadOnly, limit),
                request.getHeader("X-Request-Id"));
    }

    @PatchMapping("/events/read")
    public ApiResponse<InboxEventView> markRead(
            @RequestParam(name = "event_key") String eventKey,
            HttpServletRequest request) {
        return ApiResponse.ok(inboxService.markRead(eventKey), request.getHeader("X-Request-Id"));
    }
}
