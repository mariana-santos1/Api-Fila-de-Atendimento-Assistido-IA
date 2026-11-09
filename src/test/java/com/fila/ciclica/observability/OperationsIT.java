package com.fila.ciclica.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(ProofReporter.class)
class OperationsIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("C36 health reports application and database")
    void healthReportsApplicationAndDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.ping.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("C37 transition log carries cycle and statuses")
    void transitionLogCarriesCycleAndStatuses() throws Exception {
        String token = adminToken();
        String prefix = uniquePrefix();
        long queueId = createQueue(token, uniqueName(), prefix);
        String customerName = "Sensitive Customer " + UUID.randomUUID();
        createTicket(token, queueId, customerName, "NORMAL");

        Logger logger = (Logger) LoggerFactory.getLogger(TicketTransitionLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            mockMvc.perform(post("/api/v1/queues/{queueId}/calls/next", queueId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            String calledBody = mockMvc.perform(get("/api/v1/queues/{queueId}/state", queueId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            String publicId = JsonPath.read(calledBody, "$.calledTicket.publicId");

            mockMvc.perform(post("/api/v1/tickets/{publicId}/complete", publicId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        List<String> messages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        String logText = String.join("\n", messages);

        assertThat(logText).contains("queueId=" + queueId);
        assertThat(logText).contains("code=" + prefix + "-0001");
        assertThat(logText).contains("cycle=1");
        assertThat(logText).contains("from=WAITING").contains("to=CALLED");
        assertThat(logText).contains("from=CALLED").contains("to=COMPLETED");
        assertThat(logText).doesNotContain(customerName);
        assertThat(messages).isNotEmpty();
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
                                  "description": "queue for operations tests",
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
        return "Operations Queue " + UUID.randomUUID();
    }

    private String uniquePrefix() {
        return "O" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
