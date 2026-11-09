package com.fila.ciclica.api.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(ProofReporter.class)
class QueueIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    @DisplayName("C11 queue creation opens cycle one")
    void queueCreationOpensCycleOne() throws Exception {
        String token = adminToken();
        String name = uniqueName("create");
        String prefix = uniquePrefix();

        String body = mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 1, 2, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.prefix").value(prefix))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.weightNormal").value(1))
                .andExpect(jsonPath("$.weightPreferential").value(2))
                .andExpect(jsonPath("$.weightUrgent").value(3))
                .andExpect(jsonPath("$.activeCycleNumber").value(1))
                .andExpect(jsonPath("$.activeCycleStatus").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        Queue queue = queueRepository.findById(id.longValue()).orElseThrow();
        Cycle cycle = cycleRepository.findByActiveQueueId(queue.getId()).orElseThrow();
        assertThat(cycle.getNumber()).isEqualTo(1);
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(cycle.getActiveQueueId()).isEqualTo(queue.getId());
        assertThat(cycle.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("C12 invalid weights return 400")
    void invalidWeightsReturn400() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(uniqueName("invalid-range"), uniquePrefix(), 0, 2, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.weightNormal").exists());

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(uniqueName("invalid-order"), uniquePrefix(), 5, 3, 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.weightNormal").exists());

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(uniqueName("invalid-max"), uniquePrefix(), 1, 2, 11)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.weightUrgent").exists());

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(uniqueName("invalid-preferential"), uniquePrefix(), 1, 3, 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.weightPreferential").exists());
    }

    @Test
    @DisplayName("C13 duplicate queue returns 409")
    void duplicateQueueReturns409() throws Exception {
        String token = adminToken();
        String name = uniqueName("duplicate");
        String prefix = uniquePrefix();

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 1, 2, 3)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, uniquePrefix(), 1, 2, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUEUE"));

        mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(uniqueName("duplicate-prefix"), prefix, 1, 2, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUEUE"));
    }

    @Test
    @DisplayName("C14 queue update keeps ticket cycle weight")
    void queueUpdateKeepsTicketCycleWeight() throws Exception {
        String token = adminToken();
        String name = uniqueName("update");
        String prefix = uniquePrefix();

        String body = mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 1, 2, 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        Queue queue = queueRepository.findById(id.longValue()).orElseThrow();
        Cycle cycle = cycleRepository.findByActiveQueueId(queue.getId()).orElseThrow();

        Ticket ticket = newTicket(queue, cycle, prefix + "-9001", Priority.URGENT, 3);
        ticketRepository.saveAndFlush(ticket);

        mockMvc.perform(put("/api/v1/queues/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 3, 5, 7)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightNormal").value(3))
                .andExpect(jsonPath("$.weightPreferential").value(5))
                .andExpect(jsonPath("$.weightUrgent").value(7));

        Ticket reloaded = ticketRepository.findByPublicId(ticket.getPublicId()).orElseThrow();
        assertThat(reloaded.getCycleWeight()).isEqualTo(3);
        assertThat(reloaded.getPriority()).isEqualTo(Priority.URGENT);
    }

    @Test
    @DisplayName("C15 queue deactivation guards active tickets")
    void queueDeactivationGuardsActiveTickets() throws Exception {
        String token = adminToken();
        String name = uniqueName("deactivate");
        String prefix = uniquePrefix();

        String body = mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 1, 2, 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        Queue queue = queueRepository.findById(id.longValue()).orElseThrow();
        Cycle cycle = cycleRepository.findByActiveQueueId(queue.getId()).orElseThrow();
        Ticket ticket = ticketRepository.saveAndFlush(
                newTicket(queue, cycle, prefix + "-9002", Priority.NORMAL, 1));

        mockMvc.perform(delete("/api/v1/queues/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_HAS_ACTIVE_TICKETS"));

        ticket.setStatus(TicketStatus.CALLED);
        ticket.setCallsInCycle(1);
        ticket = ticketRepository.saveAndFlush(ticket);

        mockMvc.perform(delete("/api/v1/queues/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUEUE_HAS_ACTIVE_TICKETS"));

        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setFinishedAt(Instant.now());
        ticketRepository.saveAndFlush(ticket);

        mockMvc.perform(delete("/api/v1/queues/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Queue deactivated = queueRepository.findById(id.longValue()).orElseThrow();
        assertThat(deactivated.isActive()).isFalse();

        Cycle closed = cycleRepository.findById(cycle.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(closed.getActiveQueueId()).isNull();
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    @DisplayName("C16 queue state returns cycle counts and called ticket")
    void queueStateReturnsCycleCountsAndCalledTicket() throws Exception {
        String token = adminToken();
        String name = uniqueName("state");
        String prefix = uniquePrefix();

        String body = mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 1, 2, 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/queues/{id}/state", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueId").value(id))
                .andExpect(jsonPath("$.queueName").value(name))
                .andExpect(jsonPath("$.cycleNumber").value(1))
                .andExpect(jsonPath("$.waiting").value(0))
                .andExpect(jsonPath("$.called").value(0))
                .andExpect(jsonPath("$.completed").value(0))
                .andExpect(jsonPath("$.noShow").value(0))
                .andExpect(jsonPath("$.cancelled").value(0))
                .andExpect(jsonPath("$.calledTicket").value(org.hamcrest.Matchers.nullValue()));

        Queue queue = queueRepository.findById(id.longValue()).orElseThrow();
        Cycle cycle = cycleRepository.findByActiveQueueId(queue.getId()).orElseThrow();
        Ticket called = ticketRepository.saveAndFlush(newTicket(
                queue, cycle, prefix + "-9101", Priority.URGENT, 3, TicketStatus.CALLED));
        ticketRepository.saveAndFlush(newTicket(
                queue, cycle, prefix + "-9102", Priority.NORMAL, 1, TicketStatus.WAITING));
        ticketRepository.saveAndFlush(newTicket(
                queue, cycle, prefix + "-9103", Priority.NORMAL, 1, TicketStatus.COMPLETED));
        ticketRepository.saveAndFlush(newTicket(
                queue, cycle, prefix + "-9104", Priority.NORMAL, 1, TicketStatus.NO_SHOW));
        ticketRepository.saveAndFlush(newTicket(
                queue, cycle, prefix + "-9105", Priority.NORMAL, 1, TicketStatus.CANCELLED));

        mockMvc.perform(get("/api/v1/queues/{id}/state", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleNumber").value(1))
                .andExpect(jsonPath("$.waiting").value(1))
                .andExpect(jsonPath("$.called").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.noShow").value(1))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.calledTicket.publicId").value(called.getPublicId()))
                .andExpect(jsonPath("$.calledTicket.code").value(called.getCode()))
                .andExpect(jsonPath("$.calledTicket.status").value("CALLED"));

        mockMvc.perform(get("/api/v1/queues/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.prefix").value(prefix))
                .andExpect(jsonPath("$.activeCycleNumber").value(1));

        String listBody = mockMvc.perform(get("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .param("active", "true")
                        .param("page", "0")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        java.util.List<Integer> listedIds = JsonPath.read(listBody, "$.content[*].id");
        assertThat(listedIds).contains(id.intValue());

        mockMvc.perform(get("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));

        mockMvc.perform(get("/api/v1/queues/{id}", 999999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUEUE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/queues/{id}/state", 999999)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUEUE_NOT_FOUND"));
    }

    private Ticket newTicket(Queue queue, Cycle cycle, String code, Priority priority, int weight) {
        return newTicket(queue, cycle, code, priority, weight, TicketStatus.WAITING);
    }

    private Ticket newTicket(
            Queue queue, Cycle cycle, String code, Priority priority, int weight, TicketStatus status) {
        Instant now = Instant.now();
        Ticket ticket = new Ticket();
        ticket.setPublicId(UUID.randomUUID().toString());
        ticket.setQueue(queue);
        ticket.setCycle(cycle);
        ticket.setCode(code);
        ticket.setCustomerName("Customer " + code);
        ticket.setPriority(priority);
        ticket.setStatus(status);
        ticket.setCallsInCycle(0);
        ticket.setCycleWeight(weight);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return ticket;
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

    private String queueRequest(String name, String prefix, int normal, int preferential, int urgent) {
        return """
                {
                  "name": "%s",
                  "prefix": "%s",
                  "description": "queue for integration tests",
                  "weightNormal": %d,
                  "weightPreferential": %d,
                  "weightUrgent": %d
                }
                """.formatted(name, prefix, normal, preferential, urgent);
    }

    private String uniqueName(String prefix) {
        return "Queue " + prefix + " " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "Q" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
