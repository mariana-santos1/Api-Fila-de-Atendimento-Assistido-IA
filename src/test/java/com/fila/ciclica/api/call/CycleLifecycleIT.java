package com.fila.ciclica.api.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(ProofReporter.class)
class CycleLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Test
    @DisplayName("C32 cycle close opens next and requeues no show")
    void cycleCloseOpensNextAndRequeuesNoShow() throws Exception {
        String token = adminToken();
        String name = uniqueName("rollover");
        String prefix = uniquePrefix();
        long queueId = createQueue(token, name, prefix);

        String noShowPublicId = createTicket(token, queueId, "No Show Customer", "NORMAL");
        String completedPublicId = createTicket(token, queueId, "Completed Customer", "NORMAL");

        String firstCalled = callNext(token, queueId);
        assertThat(firstCalled).isEqualTo(noShowPublicId);

        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-show", noShowPublicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        Cycle firstCycle = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        Long firstCycleId = firstCycle.getId();
        assertThat(firstCycle.getNumber()).isEqualTo(1);

        mockMvc.perform(put("/api/v1/queues/{id}", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 2, 3, 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightNormal").value(2));

        String secondCalled = callNext(token, queueId);
        assertThat(secondCalled).isEqualTo(completedPublicId);
        mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", completedPublicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        Cycle closed = cycleRepository.findById(firstCycleId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(closed.getActiveQueueId()).isNull();

        Cycle next = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        assertThat(next.getNumber()).isEqualTo(2);
        assertThat(next.getStatus()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(next.getActiveQueueId()).isEqualTo(queueId);
        assertThat(next.getStartedAt()).isNotNull();

        Ticket requeued = ticketRepository.findByPublicId(noShowPublicId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(TicketStatus.WAITING);
        assertThat(requeued.getCallsInCycle()).isZero();
        assertThat(requeued.getCycleWeight()).isEqualTo(2);
        assertThat(requeued.getCycle().getId()).isEqualTo(next.getId());
        assertThat(requeued.getPriority()).isEqualTo(Priority.NORMAL);
        assertThat(requeued.getCalledAt()).isNull();
        assertThat(requeued.getFinishedAt()).isNull();

        Ticket completed = ticketRepository.findByPublicId(completedPublicId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        assertThat(completed.getCycle().getId()).isEqualTo(firstCycleId);

        assertThat(queueRepository.findById(queueId).orElseThrow().getWeightNormal()).isEqualTo(2);
    }

    private String callNext(String token, long queueId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.publicId");
    }

    private String createTicket(String token, long queueId, String customerName, String priority)
            throws Exception {
        String body = mockMvc.perform(post("/api/v1/queues/{queueId}/tickets", queueId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"%s","priority":"%s"}
                                """.formatted(customerName, priority)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$.publicId");
    }

    private long createQueue(String token, String name, String prefix) throws Exception {
        String body = mockMvc.perform(post("/api/v1/queues")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(queueRequest(name, prefix, 1, 2, 3)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
    }

    private String queueRequest(String name, String prefix, int normal, int preferential, int urgent) {
        return """
                {
                  "name": "%s",
                  "prefix": "%s",
                  "description": "queue for lifecycle tests",
                  "weightNormal": %d,
                  "weightPreferential": %d,
                  "weightUrgent": %d
                }
                """.formatted(name, prefix, normal, preferential, urgent);
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
        return "Lifecycle Queue " + suffix + " " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "L" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
