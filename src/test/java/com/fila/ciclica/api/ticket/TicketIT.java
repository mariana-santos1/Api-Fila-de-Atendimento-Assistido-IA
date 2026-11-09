package com.fila.ciclica.api.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ExtendWith(ProofReporter.class)
class TicketIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Test
    @DisplayName("C17 ticket creation codes and snapshots weight")
    void ticketCreationCodesAndSnapshotsWeight() throws Exception {
        String token = adminToken();
        String prefix = uniquePrefix();
        long queueId = createQueue(token, uniqueName("create"), prefix, 1, 2, 3);

        String body = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Maria Silva", "URGENT", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicId").isString())
                .andExpect(jsonPath("$.queueId").value(queueId))
                .andExpect(jsonPath("$.cycleNumber").value(1))
                .andExpect(jsonPath("$.code").value(prefix + "-0001"))
                .andExpect(jsonPath("$.customerName").value("Maria Silva"))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.callsInCycle").value(0))
                .andExpect(jsonPath("$.cycleWeight").value(3))
                .andExpect(jsonPath("$.calledAt").value(org.hamcrest.Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String publicId = JsonPath.read(body, "$.publicId");
        Ticket saved = ticketRepository.findByPublicId(publicId).orElseThrow();
        assertThat(saved.getCode()).isEqualTo(prefix + "-0001");
        assertThat(saved.getStatus()).isEqualTo(TicketStatus.WAITING);
        assertThat(saved.getCallsInCycle()).isZero();
        assertThat(saved.getCycleWeight()).isEqualTo(3);
        assertThat(saved.getPriority()).isEqualTo(Priority.URGENT);
        assertThat(saved.getPublicId()).hasSize(36);

        mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Joao Souza", "PREFERENTIAL", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(prefix + "-0002"))
                .andExpect(jsonPath("$.cycleWeight").value(2));
    }

    @Test
    @DisplayName("C18 omitted priority defaults to normal")
    void omittedPriorityDefaultsToNormal() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("default"), uniquePrefix(), 1, 2, 3);

        mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Ana Lima", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.cycleWeight").value(1));

        mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Carlos Dias"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.cycleWeight").value(1));
    }

    @Test
    @DisplayName("C19 invalid priority returns 400")
    void invalidPriorityReturns400() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("invalid"), uniquePrefix(), 1, 2, 3);

        mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Invalid Priority", "VIP", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRIORITY"));
    }

    @Test
    @DisplayName("C20 external reference is idempotent")
    void externalReferenceIsIdempotent() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("idempotent"), uniquePrefix(), 1, 2, 3);
        String reference = "ref-" + UUID.randomUUID();

        String first = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Idempotent User", "NORMAL", reference)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Idempotent User", "URGENT", reference)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstPublicId = JsonPath.read(first, "$.publicId");
        String secondPublicId = JsonPath.read(second, "$.publicId");
        assertThat(secondPublicId).isEqualTo(firstPublicId);

        Ticket stored = ticketRepository
                .findByQueueIdAndExternalReference(queueId, reference)
                .orElseThrow();
        assertThat(stored.getPublicId()).isEqualTo(firstPublicId);
        assertThat(stored.getPriority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    @DisplayName("C21 inactive queue rejects ticket")
    void inactiveQueueRejectsTicket() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("inactive"), uniquePrefix(), 1, 2, 3);

        mockMvc.perform(delete("/api/v1/queues/{id}", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Blocked User", "NORMAL", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_INACTIVE"));
    }

    @Test
    @DisplayName("C23 priority change is locked after call")
    void priorityChangeIsLockedAfterCall() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("priority"), uniquePrefix(), 1, 2, 3);

        String created = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Priority User", "NORMAL", null)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicId = JsonPath.read(created, "$.publicId");

        mockMvc.perform(patch("/api/v1/tickets/{publicId}/priority", publicId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":"PREFERENTIAL"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("PREFERENTIAL"))
                .andExpect(jsonPath("$.cycleWeight").value(2));

        Ticket called = ticketRepository.findByPublicId(publicId).orElseThrow();
        called.setStatus(TicketStatus.CALLED);
        called.setCallsInCycle(1);
        ticketRepository.saveAndFlush(called);

        mockMvc.perform(patch("/api/v1/tickets/{publicId}/priority", publicId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":"URGENT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIORITY_LOCKED"));

        Ticket reloaded = ticketRepository.findByPublicId(publicId).orElseThrow();
        assertThat(reloaded.getPriority()).isEqualTo(Priority.PREFERENTIAL);
        assertThat(reloaded.getCycleWeight()).isEqualTo(2);

        reloaded.setStatus(TicketStatus.WAITING);
        ticketRepository.saveAndFlush(reloaded);

        mockMvc.perform(patch("/api/v1/tickets/{publicId}/priority", publicId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"priority":"URGENT"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRIORITY_LOCKED"));
    }

    @Test
    @DisplayName("C24 cancel evaluates cycle closure")
    void cancelEvaluatesCycleClosure() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("cancel"), uniquePrefix(), 1, 2, 3);

        String first = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Cancel Me", "NORMAL", null)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String publicId = JsonPath.read(first, "$.publicId");

        String second = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Already Done", "NORMAL", null)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String completedPublicId = JsonPath.read(second, "$.publicId");

        Ticket completed = ticketRepository.findByPublicId(completedPublicId).orElseThrow();
        completed.setStatus(TicketStatus.COMPLETED);
        completed.setFinishedAt(java.time.Instant.now());
        ticketRepository.saveAndFlush(completed);

        mockMvc.perform(delete("/api/v1/tickets/{publicId}", completedPublicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        Ticket ticket = ticketRepository.findByPublicId(publicId).orElseThrow();
        Long cycleId = ticket.getCycle().getId();

        Cycle active = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(CycleStatus.ACTIVE);

        mockMvc.perform(delete("/api/v1/tickets/{publicId}", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Ticket cancelled = ticketRepository.findByPublicId(publicId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelled.getFinishedAt()).isNotNull();

        Cycle closed = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(closed.getActiveQueueId()).isNull();
        assertThat(closed.getClosedAt()).isNotNull();

        long calledQueueId = createQueue(token, uniqueName("cancel-called"), uniquePrefix(), 1, 2, 3);
        String calledBody = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", calledQueueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketRequest("Called Cancel", "NORMAL", null)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String calledPublicId = JsonPath.read(calledBody, "$.publicId");

        mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", calledQueueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/tickets/{publicId}", calledPublicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Ticket cancelledCalled = ticketRepository.findByPublicId(calledPublicId).orElseThrow();
        assertThat(cancelledCalled.getStatus()).isEqualTo(TicketStatus.CANCELLED);
        assertThat(cancelledCalled.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("C22 ticket list filters and paginates")
    void ticketListFiltersAndPaginates() throws Exception {
        String token = adminToken();
        String prefix = uniquePrefix();
        long queueId = createQueue(token, uniqueName("list"), prefix, 1, 2, 3);

        Queue queue = queueRepository.findById(queueId).orElseThrow();
        Cycle cycle = cycleRepository.findByActiveQueueId(queueId).orElseThrow();

        Ticket waitingNormal = ticketRepository.saveAndFlush(
                newTicket(queue, cycle, prefix + "-8001", Priority.NORMAL, TicketStatus.WAITING));
        Ticket waitingUrgent = ticketRepository.saveAndFlush(
                newTicket(queue, cycle, prefix + "-8002", Priority.URGENT, TicketStatus.WAITING));
        ticketRepository.saveAndFlush(
                newTicket(queue, cycle, prefix + "-8003", Priority.PREFERENTIAL, TicketStatus.CALLED));
        ticketRepository.saveAndFlush(
                newTicket(queue, cycle, prefix + "-8004", Priority.NORMAL, TicketStatus.COMPLETED));
        ticketRepository.saveAndFlush(
                newTicket(queue, cycle, prefix + "-8005", Priority.URGENT, TicketStatus.CANCELLED));

        mockMvc.perform(get("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("WAITING"))
                .andExpect(jsonPath("$.content[1].status").value("WAITING"));

        mockMvc.perform(get("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .param("priority", "URGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].priority").value("URGENT"))
                .andExpect(jsonPath("$.content[1].priority").value("URGENT"));

        mockMvc.perform(get("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .param("status", "WAITING")
                        .param("priority", "NORMAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].publicId").value(waitingNormal.getPublicId()));

        mockMvc.perform(get("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3));

        mockMvc.perform(get("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/tickets/{publicId}", waitingUrgent.getPublicId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(waitingUrgent.getPublicId()))
                .andExpect(jsonPath("$.code").value(prefix + "-8002"));

        mockMvc.perform(get("/api/v1/tickets/{publicId}", UUID.randomUUID().toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/queues/{queueId}/tickets", 999999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUEUE_NOT_FOUND"));
    }

    private Ticket newTicket(
            Queue queue, Cycle cycle, String code, Priority priority, TicketStatus status) {
        java.time.Instant now = java.time.Instant.now();
        Ticket ticket = new Ticket();
        ticket.setPublicId(UUID.randomUUID().toString());
        ticket.setQueue(queue);
        ticket.setCycle(cycle);
        ticket.setCode(code);
        ticket.setCustomerName("Customer " + code);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setCallsInCycle(status == TicketStatus.CALLED ? 1 : 0);
        ticket.setCycleWeight(queue.weightFor(priority));
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return ticket;
    }

    @Test
    @DisplayName("concurrent external reference creates one ticket")
    void concurrentExternalReferenceCreatesOneTicket() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("concurrent-ref"), uniquePrefix(), 1, 2, 3);
        String reference = "concurrent-ref-" + UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<MvcResult> request = () -> {
            start.await(10, TimeUnit.SECONDS);
            return mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ticketRequest("Concurrent User", "NORMAL", reference)))
                    .andReturn();
        };

        try {
            Future<MvcResult> first = pool.submit(request);
            Future<MvcResult> second = pool.submit(request);
            start.countDown();

            MvcResult firstResult = first.get(60, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(60, TimeUnit.SECONDS);

            assertThat(List.of(
                            firstResult.getResponse().getStatus(),
                            secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(200, 201);

            String firstPublicId = JsonPath.read(
                    firstResult.getResponse().getContentAsString(), "$.publicId");
            String secondPublicId = JsonPath.read(
                    secondResult.getResponse().getContentAsString(), "$.publicId");
            assertThat(firstPublicId).isEqualTo(secondPublicId);
            assertThat(ticketRepository.findByQueueIdAndExternalReference(queueId, reference))
                    .isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    private long createQueue(
            String token, String name, String prefix, int normal, int preferential, int urgent)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "prefix": "%s",
                                  "description": "queue for ticket tests",
                                  "weightNormal": %d,
                                  "weightPreferential": %d,
                                  "weightUrgent": %d
                                }
                                """.formatted(name, prefix, normal, preferential, urgent)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private String ticketRequest(String customerName, String priority, String externalReference) {
        StringBuilder json = new StringBuilder("{\"customerName\":\"").append(customerName).append("\"");
        if (priority != null) {
            json.append(",\"priority\":\"").append(priority).append("\"");
        }
        if (externalReference != null) {
            json.append(",\"externalReference\":\"").append(externalReference).append("\"");
        }
        return json.append('}').toString();
    }

    private String adminToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@test.local","password":"admin12345"}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(body, "$.accessToken");
    }

    private String uniqueName(String suffix) {
        return "Ticket Queue " + suffix + " " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
