package com.fila.ciclica.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleCallRepository;
import com.fila.ciclica.repository.CycleRepository;
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
class CyclicQueueE2EIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private CycleCallRepository cycleCallRepository;

    @Test
    @DisplayName("E2E cyclic queue runs calls outcomes and cycle rollover")
    void cyclicQueueRunsCallsOutcomesAndCycleRollover() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName(), uniquePrefix());
        String urgent = createTicket(token, queueId, "Urgent Customer", "URGENT");
        String normal = createTicket(token, queueId, "Normal Customer", "NORMAL");

        assertThat(callNext(token, queueId)).isEqualTo(urgent);
        noAnswer(token, urgent, "WAITING", 1, 1);

        assertThat(callNext(token, queueId)).isEqualTo(normal);
        mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", normal)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(callNext(token, queueId)).isEqualTo(urgent);
        noAnswer(token, urgent, "WAITING", 2, 1);

        assertThat(callNext(token, queueId)).isEqualTo(urgent);
        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-answer", urgent)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Ticket requeued = ticketRepository.findByPublicId(urgent).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(TicketStatus.WAITING);
        assertThat(requeued.getCallsInCycle()).isZero();
        assertThat(requeued.getCycleWeight()).isEqualTo(3);

        Cycle next = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        assertThat(next.getNumber()).isEqualTo(2);
        assertThat(next.getStatus()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(requeued.getCycle().getId()).isEqualTo(next.getId());

        Cycle closed = cycleRepository.findByQueueIdAndStatus(queueId, CycleStatus.CLOSED).get(0);
        assertThat(closed.getNumber()).isEqualTo(1);
        assertThat(closed.getActiveQueueId()).isNull();
        assertThat(cycleCallRepository.countByCycleId(closed.getId())).isEqualTo(4);

        Ticket completed = ticketRepository.findByPublicId(normal).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        assertThat(completed.getCycle().getId()).isEqualTo(closed.getId());

        mockMvc.perform(get("/api/v1/queues/{queueId}/state", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleNumber").value(2))
                .andExpect(jsonPath("$.waiting").value(1))
                .andExpect(jsonPath("$.called").value(0));
    }

    private void noAnswer(
            String token, String publicId, String expectedStatus, int expectedCalls, int expectedCycle)
            throws Exception {
        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-answer", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.callsInCycle").value(expectedCalls))
                .andExpect(jsonPath("$.cycleNumber").value(expectedCycle));
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
                        .content("""
                                {
                                  "name": "%s",
                                  "prefix": "%s",
                                  "description": "queue for e2e tests",
                                  "weightNormal": 1,
                                  "weightPreferential": 2,
                                  "weightUrgent": 3
                                }
                                """.formatted(name, prefix)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Number id = JsonPath.read(body, "$.id");
        return id.longValue();
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

    private String uniqueName() {
        return "E2E Queue " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "E" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
