package com.fila.ciclica.observability;

import com.fila.ciclica.domain.TicketStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes one INFO line per ticket state transition. The line carries operational identifiers only
 * (queue, ticket code, cycle and statuses) and never customer personal data.
 */
@Component
public class TicketTransitionLogger {

    private static final Logger LOG = LoggerFactory.getLogger(TicketTransitionLogger.class);

    public void log(
            Long queueId,
            String ticketCode,
            int cycleNumber,
            TicketStatus previousStatus,
            TicketStatus newStatus) {
        LOG.info(
                "Ticket transition queueId={} code={} cycle={} from={} to={}",
                queueId,
                ticketCode,
                cycleNumber,
                previousStatus,
                newStatus);
    }
}
