package com.fila.ciclica.api.queue;

import com.fila.ciclica.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/queues")
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueueResponse> create(@Valid @RequestBody CreateQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public QueueResponse update(@PathVariable Long id, @Valid @RequestBody UpdateQueueRequest request) {
        return queueService.update(id, request);
    }

    @GetMapping
    public PageResponse<QueueResponse> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queueService.list(active, page, size);
    }

    @GetMapping("/{id}")
    public QueueResponse getById(@PathVariable Long id) {
        return queueService.getById(id);
    }

    @GetMapping("/{id}/state")
    public QueueStateResponse state(@PathVariable Long id) {
        return queueService.state(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        queueService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
