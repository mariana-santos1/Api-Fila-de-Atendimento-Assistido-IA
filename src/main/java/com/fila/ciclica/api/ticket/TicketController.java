package com.fila.ciclica.api.ticket;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/queues/{queueId}/tickets")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ResponseEntity<TicketResponse> create(
            @PathVariable Long queueId, @Valid @RequestBody CreateTicketRequest request) {
        TicketCreationResult result = ticketService.create(queueId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.response());
    }
}
