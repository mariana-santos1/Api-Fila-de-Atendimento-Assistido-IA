package com.fila.ciclica.api.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class TicketTransitionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private CycleCallRepository cycleCallRepository;

    @Test
    @DisplayName("C29 complete sets completed and closes when last")
    void completeSetsCompletedAndClosesWhenLast() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("complete"), uniquePrefix());
        String publicId = createTicket(token, queueId, "Complete Customer", "NORMAL");

        callNext(token, queueId);

        mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        Ticket ticket = ticketRepository.findByPublicId(publicId).orElseThrow();
        Long closedCycleId = ticket.getCycle().getId();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.COMPLETED);
        assertThat(ticket.getFinishedAt()).isNotNull();

        Cycle closed = cycleRepository.findById(closedCycleId).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(closed.getActiveQueueId()).isNull();
        assertThat(closed.getClosedAt()).isNotNull();

        Cycle next = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        assertThat(next.getNumber()).isEqualTo(2);
        assertThat(next.getStatus()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(next.getActiveQueueId()).isEqualTo(queueId);
        assertThat(next.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("C30 no answer retries until weight then no show")
    void noAnswerRetriesUntilWeightThenNoShow() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("no-answer"), uniquePrefix());
        String publicId = createTicket(token, queueId, "Retry Customer", "PREFERENTIAL");

        callNext(token, queueId);
        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-answer", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.callsInCycle").value(1))
                .andExpect(jsonPath("$.cycleNumber").value(1));

        Ticket afterFirstAttempt = ticketRepository.findByPublicId(publicId).orElseThrow();
        Long firstCycleId = afterFirstAttempt.getCycle().getId();
        Cycle activeFirstCycle = cycleRepository.findById(firstCycleId).orElseThrow();
        assertThat(activeFirstCycle.getStatus()).isEqualTo(CycleStatus.ACTIVE);

        callNext(token, queueId);
        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-answer", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        Ticket afterSecondAttempt = ticketRepository.findByPublicId(publicId).orElseThrow();
        assertThat(afterSecondAttempt.getStatus()).isEqualTo(TicketStatus.WAITING);
        assertThat(afterSecondAttempt.getCallsInCycle()).isZero();
        Cycle secondCycle = cycleRepository.findById(afterSecondAttempt.getCycle().getId()).orElseThrow();
        assertThat(secondCycle.getNumber()).isEqualTo(2);
        assertThat(afterSecondAttempt.getCycleWeight()).isEqualTo(2);
        assertThat(afterSecondAttempt.getCalledAt()).isNull();
        assertThat(afterSecondAttempt.getFinishedAt()).isNull();

        Cycle firstCycle = cycleRepository.findById(firstCycleId).orElseThrow();
        assertThat(firstCycle.getStatus()).isEqualTo(CycleStatus.CLOSED);
        assertThat(cycleCallRepository.countByCycleId(firstCycleId)).isEqualTo(2);
        assertThat(cycleRepository.findByActiveQueueId(queueId).orElseThrow().getNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("C31 explicit no show sets no show")
    void explicitNoShowSetsNoShow() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("no-show"), uniquePrefix());
        String toNoShow = createTicket(token, queueId, "Absent Customer", "URGENT");
        createTicket(token, queueId, "Still Waiting", "NORMAL");

        String called = callNext(token, queueId);
        assertThat(called).isEqualTo(toNoShow);

        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-show", toNoShow)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.callsInCycle").value(1))
                .andExpect(jsonPath("$.finishedAt").isNotEmpty());

        Ticket ticket = ticketRepository.findByPublicId(toNoShow).orElseThrow();
        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.NO_SHOW);
        assertThat(ticket.getCallsInCycle()).isEqualTo(1);
        Cycle cycle = cycleRepository.findById(ticket.getCycle().getId()).orElseThrow();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(cycle.getNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("C34 invalid transition returns 409")
    void invalidTransitionReturns409() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("invalid"), uniquePrefix());
        String publicId = createTicket(token, queueId, "Invalid Customer", "NORMAL");

        for (String action : new String[] {"complete", "no-answer", "no-show"}) {
            mockMvc.perform(post("/api/v1/tickets/{publicId}/{action}", publicId, action)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        }

        mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));

        callNext(token, queueId);
        mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(post("/api/v1/tickets/{publicId}/no-answer", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mockMvc.perform(delete("/api/v1/tickets/{publicId}", publicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
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
                  "description": "queue for transition tests",
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
        return "Transition Queue " + suffix + " " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "N" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
