package com.fila.ciclica.api.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleCall;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleCallRepository;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.TicketRepository;
import com.fila.ciclica.repository.UserRepository;
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
class CallNextIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private CycleCallRepository cycleCallRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("C26 call increments and records the call")
    void callIncrementsAndRecordsTheCall() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("call"), uniquePrefix());
        createTicket(token, queueId, "First Customer", "URGENT");
        createTicket(token, queueId, "Second Customer", "NORMAL");

        String body = mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CALLED"))
                .andExpect(jsonPath("$.callsInCycle").value(1))
                .andExpect(jsonPath("$.calledAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String publicId = JsonPath.read(body, "$.publicId");
        Ticket called = ticketRepository.findByPublicId(publicId).orElseThrow();
        assertThat(called.getStatus()).isEqualTo(TicketStatus.CALLED);
        assertThat(called.getCallsInCycle()).isEqualTo(1);
        assertThat(called.getCalledAt()).isNotNull();
        assertThat(called.getCustomerName()).isEqualTo("First Customer");

        Cycle cycle = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        assertThat(cycleCallRepository.countByCycleId(cycle.getId())).isEqualTo(1);
        CycleCall cycleCall =
                cycleCallRepository.findTopByCycleIdOrderBySequenceNumberDesc(cycle.getId()).orElseThrow();
        assertThat(cycleCall.getCycle().getId()).isEqualTo(cycle.getId());
        assertThat(cycleCall.getTicket().getId()).isEqualTo(called.getId());
        assertThat(cycleCall.getSequenceNumber()).isEqualTo(1);
        assertThat(cycleCall.getAttendant().getId())
                .isEqualTo(userRepository.findByEmail("admin@test.local").orElseThrow().getId());
        assertThat(cycleCall.getCalledAt()).isNotNull();
    }

    @Test
    @DisplayName("C27 active call blocks another call")
    void activeCallBlocksAnotherCall() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("blocked"), uniquePrefix());
        String firstPublicId = createTicket(token, queueId, "Called Customer", "NORMAL");
        String secondPublicId = createTicket(token, queueId, "Waiting Customer", "NORMAL");

        mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(firstPublicId));

        mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_CALL_EXISTS"));

        Ticket first = ticketRepository.findByPublicId(firstPublicId).orElseThrow();
        Ticket second = ticketRepository.findByPublicId(secondPublicId).orElseThrow();
        assertThat(first.getStatus()).isEqualTo(TicketStatus.CALLED);
        assertThat(first.getCallsInCycle()).isEqualTo(1);
        assertThat(second.getStatus()).isEqualTo(TicketStatus.WAITING);
        assertThat(second.getCallsInCycle()).isZero();
        Cycle cycle = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        assertThat(cycleCallRepository.countByCycleId(cycle.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("C28 no eligible ticket returns 409")
    void noEligibleTicketReturns409() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName("empty"), uniquePrefix());
        String publicId = createTicket(token, queueId, "Exhausted Customer", "NORMAL");

        Ticket ticket = ticketRepository.findByPublicId(publicId).orElseThrow();
        ticket.setCallsInCycle(ticket.getCycleWeight());
        ticketRepository.saveAndFlush(ticket);
        Long cycleId = ticket.getCycle().getId();

        mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_TICKET_AVAILABLE"));

        Ticket unchanged = ticketRepository.findByPublicId(publicId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(TicketStatus.WAITING);
        assertThat(unchanged.getCallsInCycle()).isEqualTo(unchanged.getCycleWeight());
        assertThat(cycleCallRepository.countByCycleId(cycleId)).isZero();

        Cycle cycle = cycleRepository.findById(cycleId).orElseThrow();
        assertThat(cycle.getStatus()).isEqualTo(CycleStatus.ACTIVE);
        assertThat(cycle.getActiveQueueId()).isEqualTo(queueId);
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
                                  "description": "queue for call tests",
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

    private String uniqueName(String suffix) {
        return "Call Queue " + suffix + " " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "C" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
