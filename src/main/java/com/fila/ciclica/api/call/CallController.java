package com.fila.ciclica.api.call;

import com.fila.ciclica.api.ticket.TicketResponse;
import java.security.Principal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CallController {

    private final CallService callService;

    public CallController(CallService callService) {
        this.callService = callService;
    }

    @PostMapping("/queues/{queueId}/calls/next")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public TicketResponse callNext(@PathVariable Long queueId, Principal principal) {
        return callService.callNext(queueId, principal.getName());
    }
}
