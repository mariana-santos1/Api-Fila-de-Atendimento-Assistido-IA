package com.fila.ciclica.api.ticket;

import com.fila.ciclica.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PatchMapping("/tickets/{publicId}/priority")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public TicketResponse changePriority(
            @PathVariable String publicId, @Valid @RequestBody PriorityChangeRequest request) {
        return ticketService.changePriority(publicId, request);
    }

    @DeleteMapping("/tickets/{publicId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ATTENDANT')")
    public ResponseEntity<Void> cancel(@PathVariable String publicId) {
        ticketService.cancel(publicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/queues/{queueId}/tickets")
    public PageResponse<TicketResponse> list(
            @PathVariable Long queueId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ticketService.list(queueId, status, priority, page, size);
    }

    @GetMapping("/tickets/{publicId}")
    public TicketResponse getByPublicId(@PathVariable String publicId) {
        return ticketService.getByPublicId(publicId);
    }
}
