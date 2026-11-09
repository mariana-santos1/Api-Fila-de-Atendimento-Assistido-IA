package com.fila.ciclica.api.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
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
class TicketIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

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
