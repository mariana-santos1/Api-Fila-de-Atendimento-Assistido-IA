package com.fila.ciclica.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleCallRepository;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.TicketRepository;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

@ExtendWith(ProofReporter.class)
class CallNextConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private CycleCallRepository cycleCallRepository;

    @Test
    @DisplayName("C35 concurrent calls allow only one winner")
    void concurrentCallsAllowOnlyOneWinner() throws Exception {
        String token = adminToken();
        long queueId = createQueue(token, uniqueName(), uniquePrefix());
        createTicket(token, queueId, "Concurrent One", "NORMAL");
        createTicket(token, queueId, "Concurrent Two", "NORMAL");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Outcome> request = () -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                var result = mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                                .header("Authorization", "Bearer " + token))
                        .andReturn();
                int httpStatus = result.getResponse().getStatus();
                String body = result.getResponse().getContentAsString();
                String code = body.contains("\"code\"") ? JsonPath.read(body, "$.code") : null;
                return new Outcome(httpStatus, code);
            };

            Future<Outcome> first = executor.submit(request);
            Future<Outcome> second = executor.submit(request);

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            outcomes.add(first.get(30, TimeUnit.SECONDS));
            outcomes.add(second.get(30, TimeUnit.SECONDS));

            List<Integer> statuses = outcomes.stream().map(Outcome::httpStatus).sorted().toList();
            assertThat(statuses).containsExactly(200, 409);
            Outcome loser = outcomes.stream()
                    .filter(outcome -> outcome.httpStatus() == 409)
                    .findFirst()
                    .orElseThrow();
            assertThat(loser.code()).isEqualTo("ACTIVE_CALL_EXISTS");
        } finally {
            executor.shutdownNow();
        }

        Cycle cycle = cycleRepository.findByActiveQueueId(queueId).orElseThrow();
        assertThat(cycleCallRepository.countByCycleId(cycle.getId())).isEqualTo(1);
        long called = ticketRepository.findAll().stream()
                .filter(ticket -> ticket.getQueue().getId().equals(queueId))
                .filter(ticket -> ticket.getStatus() == TicketStatus.CALLED)
                .count();
        assertThat(called).isEqualTo(1);
        assertThat(ticketRepository.findAll().stream()
                        .filter(ticket -> ticket.getQueue().getId().equals(queueId))
                        .filter(ticket -> ticket.getStatus() == TicketStatus.WAITING)
                        .count())
                .isEqualTo(1);
    }

    private record Outcome(int httpStatus, String code) {
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
                                  "description": "queue for concurrency tests",
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
        return "Concurrency Queue " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "X" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
