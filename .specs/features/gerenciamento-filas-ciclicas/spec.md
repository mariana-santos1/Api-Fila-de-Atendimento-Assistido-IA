# Gerenciamento de Filas Cíclicas Specification

**Approved**: 2026-11-08 - user: "Eu li os arquivos e acredito que esteja tudo bem. Pode implementar o arquivo de tasks e use sub agentes sempre que possível."

## Problem Statement

Filas de atendimento presenciais ou internas precisam de rodízio justo, mas com prioridade para casos urgentes e preferenciais. O controle manual gera filas paradas, chamadas repetidas sem critério e nenhuma visão confiável de quem já foi chamado no ciclo. Esta API entrega ciclos explícitos, tentativas limitadas por prioridade e transições de estado auditáveis, com autenticação por papéis.

## Goals

- [ ] Entregar uma API REST autenticada que permita ADMIN e ATTENDANT operarem filas, tickets e chamadas sem conflito de estado.
- [ ] Garantir que cada ciclo chame todos os tickets elegíveis, respeitando o máximo de tentativas por prioridade e abrindo o próximo ciclo automaticamente.
- [ ] Persistir tudo em MySQL 8.4 que sobe com `docker compose up` e é migrado por Flyway.
- [ ] Cobrir cada critério de aceitação com teste automatizado, incluindo concorrência de chamadas na mesma fila.

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
| --- | --- |
| Assistência por IA (LLM) | Decisão do usuário: "Fora do MVP". |
| Múltiplas posições de atendimento / chamadas simultâneas | Decisão do usuário: uma chamada ativa por fila. |
| Histórico e relatórios de atendimento | Não selecionado no MVP; `cycle_calls` existe apenas como suporte interno. |
| Notificações em tempo real, painel físico, WebSocket/SSE | Não faz parte do escopo da API. |
| Interface web ou mobile | A entrega é somente a API. |
| Refresh token, recuperação de senha, rate limiting, multi-tenancy | Não pedido; o MVP usa JWT de acesso simples. |
| Agendamento de ciclos por janela de tempo | O ciclo fecha por estado dos tickets, não por relógio. |
| Deploy em produção e CI/CD | O alvo é execução local com Docker Compose. |
| Documentação OpenAPI/Swagger | Não pedida; o README trará exemplos de uso. |
| Exclusão física de tickets ou filas | Tickets são cancelados; filas são desativadas. |

---

## Assumptions & Open Questions

Every ambiguity is resolved or recorded here - nothing is left silently unclear.

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Semântica de fila cíclica | Ciclo ativo por fila; rodízio circular; fecha quando não há WAITING nem CALLED | Resposta do usuário: "Rodízio circular" e "Todos chamados 1x" | y - user |
| Prioridade no ciclo | Peso = máximo de tentativas por ciclo; NORMAL=1, PREFERENTIAL=2, URGENT=3, configurável por fila | Resposta do usuário: "Peso por prioridade" e "Máximo de tentativas" | y - user |
| Ticket não atendido | "Não atendeu" devolve a WAITING enquanto houver tentativa; ao esgotar vira NO_SHOW e volta no próximo ciclo | Resposta do usuário: "NO_SHOW volta ao próximo ciclo" | y - user |
| Chamadas simultâneas | No máximo um ticket CALLED por fila; demais chamadas recebem 409 | Resposta do usuário: "Uma chamada ativa por fila" | y - user |
| Autenticação | JWT HS256, expiração de 3600 s, papéis ADMIN e ATTENDANT | Resposta do usuário: "JWT com papéis" | y - user |
| Provisionamento de usuários | ADMIN inicial por seed de ambiente; ADMIN cria/listar/desativa usuários | Resposta do usuário: "Seed admin + CRUD usuários" | y - user |
| Prioridade omitida | NORMAL | Menor surpresa para o cliente da API | n - agent default |
| Formato do código do ticket | `PREFIX-NNNN`, contador por fila, prefixo de 1 a 8 caracteres alfanuméricos maiúsculos | Código curto e legível para chamada oral | n - agent default |
| Paginação | Página padrão 20, máxima 100; page size acima de 100 retorna 400 | Evita respostas ilimitadas | n - agent default |
| Retenção de dados | Tickets concluídos/cancelados e ciclos fechados são mantidos indefinidamente no MVP | Sem requisito de arquivamento; operação local | n - agent default |
| Fuso horário | Todas as datas persistidas e retornadas em UTC | Evita ambiguidade entre aplicação e MySQL | n - agent default |
| Erros | RFC 9457 ProblemDetail com `code` e `fields` | Contrato consistente para validação e regras | n - agent default |
| Idempotência de criação | `externalReference` opcional, único por fila; repetição retorna o ticket existente com 200 | Protege contra retry do cliente | n - agent default |
| Rate limiting | N/A porque a API é interna e autenticada, sem tráfego público no MVP | Não há requisito de proteção volumétrica | n - agent default |
| Observabilidade | Actuator health/info e logs de transição; sem tracing/métricas externas | Suficiente para operação local do MVP | n - agent default |
| Falha do MySQL | A aplicação falha rápido no startup e expõe health check; sem circuit breaker | Único datastore, sem fallback possível | n - agent default |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Autenticação e controle de acesso ⭐ MVP

**User Story**: As an operator, I want to authenticate with a role so that only authorized people can manage queues and calls.

**Why P1**: Sem autenticação, qualquer cliente poderia chamar tickets e alterar filas; o usuário escolheu JWT com papéis.

**Acceptance Criteria**:
1. WHEN valid email and password are posted to `/api/v1/auth/login` THEN the system SHALL return HTTP 200 with a signed JWT access token, the user role, and `expiresIn` of 3600 seconds. <!-- AUTH-01 -->
2. IF the email does not exist or the password is wrong THEN the system SHALL return HTTP 401 with ProblemDetail code `INVALID_CREDENTIALS` and no token. <!-- AUTH-02 -->
3. IF a protected request has no JWT, an invalid signature, or an expired token THEN the system SHALL return HTTP 401 with ProblemDetail code `UNAUTHORIZED`. <!-- AUTH-03 -->
4. IF an authenticated user lacks the role required by the endpoint THEN the system SHALL return HTTP 403 with ProblemDetail code `FORBIDDEN`. <!-- AUTH-04 -->
5. The system SHALL store passwords only as BCrypt hashes and SHALL NOT include password or hash fields in any response. <!-- AUTH-05 -->

**Independent Test**: Fazer login com o admin semeado, usar o token em `GET /api/v1/queues` e confirmar 200; repetir sem token e confirmar 401.

---

### P1: Provisionamento e gestão de usuários ⭐ MVP

**User Story**: As an ADMIN, I want to create and deactivate users so that the right attendants can operate the queues.

**Why P1**: O JWT precisa de usuários reais e o ADMIN inicial precisa existir antes do primeiro login.

**Acceptance Criteria**:
1. WHEN the application starts and no user exists with `ADMIN_EMAIL` THEN the system SHALL create an active ADMIN using `ADMIN_PASSWORD`, hashed with BCrypt. <!-- USER-01 -->
2. WHEN an ADMIN posts a user with name, email, password, and role THEN the system SHALL create an active user and return HTTP 201 without the password hash. <!-- USER-02 -->
3. IF the email already belongs to a user THEN the system SHALL return HTTP 409 with code `DUPLICATE_EMAIL`. <!-- USER-03 -->
4. WHEN an ADMIN deactivates a user THEN the system SHALL reject that user's subsequent login with HTTP 401. <!-- USER-04 -->
5. WHEN an authenticated ADMIN lists users THEN the system SHALL return a paginated list ordered by name with page size at most 100. <!-- USER-05 -->

**Independent Test**: Subir a aplicação sem usuários, autenticar com o admin do seed, criar um ATTENDANT, desativá-lo e confirmar que o login dele falha.

---

### P1: Gestão de filas ⭐ MVP

**User Story**: As an ADMIN, I want to create and configure queues so that each service has its own cycle and priority weights.

**Why P1**: Toda a operação de tickets e ciclos depende de uma fila configurada.

**Acceptance Criteria**:
1. WHEN an ADMIN creates a queue with name, prefix, and weights THEN the system SHALL persist it and create cycle number 1 with status ACTIVE in the same transaction. <!-- QUEUE-01 -->
2. IF a weight is outside 1 through 10 or normal is greater than preferential or preferential is greater than urgent THEN the system SHALL return HTTP 400 with field errors. <!-- QUEUE-02 -->
3. IF the name or prefix already exists THEN the system SHALL return HTTP 409 with code `DUPLICATE_QUEUE`. <!-- QUEUE-03 -->
4. WHEN an ADMIN updates a queue THEN the system SHALL apply the change and keep the `cycleWeight` already snapshotted on tickets of the active cycle. <!-- QUEUE-04 -->
5. IF an ADMIN deactivates a queue that has WAITING or CALLED tickets THEN the system SHALL return HTTP 409 with code `QUEUE_HAS_ACTIVE_TICKETS`; otherwise it SHALL mark the queue inactive and close the active cycle. <!-- QUEUE-05 -->
6. WHEN an authenticated user reads a queue state THEN the system SHALL return the active cycle number, counts per status, and the currently CALLED ticket if any. <!-- QUEUE-06 -->

**Independent Test**: Criar uma fila com pesos 1/2/3, consultar o estado e confirmar ciclo 1 ACTIVE com contagens zeradas; tentar criar outra com o mesmo prefixo e receber 409.

---

### P1: Gestão de tickets ⭐ MVP

**User Story**: As an attendant, I want to create, list, prioritize, and cancel tickets so that the queue reflects the real demand.

**Why P1**: Sem tickets não há fila; prioridade e cancelamento são parte do CRUD escolhido.

**Acceptance Criteria**:
1. WHEN an authenticated user creates a ticket in an active queue THEN the system SHALL assign code `PREFIX-NNNN`, set status WAITING, callsInCycle 0, and snapshot the queue weight for the ticket priority as `cycleWeight`. <!-- TICKET-01 -->
2. IF priority is omitted THEN the system SHALL use `NORMAL`. <!-- TICKET-02 -->
3. IF priority is not `NORMAL`, `PREFERENTIAL`, or `URGENT` THEN the system SHALL return HTTP 400 with code `INVALID_PRIORITY`. <!-- TICKET-03 -->
4. WHEN a ticket is created with an `externalReference` already used in the same queue THEN the system SHALL return HTTP 200 with the existing ticket and SHALL NOT create a duplicate. <!-- TICKET-04 -->
5. IF the queue is inactive THEN the system SHALL return HTTP 409 with code `QUEUE_INACTIVE`. <!-- TICKET-05 -->
6. WHEN an authenticated user lists tickets THEN the system SHALL return a paginated list filtered by status and priority, with page size at most 100. <!-- TICKET-06 -->
7. WHEN an authenticated user changes the priority of a WAITING ticket with callsInCycle 0 THEN the system SHALL update priority and `cycleWeight`; IF the ticket is not WAITING or has callsInCycle greater than 0 THEN the system SHALL return HTTP 409 with code `PRIORITY_LOCKED`. <!-- TICKET-07 -->
8. WHEN an authenticated user cancels a WAITING or CALLED ticket THEN the system SHALL set status CANCELLED and evaluate cycle closure. <!-- TICKET-08 -->

**Independent Test**: Criar dois tickets na mesma fila, um sem prioridade e outro URGENT, listar por prioridade e cancelar o primeiro; confirmar os códigos e estados.

---

### P1: Chamada cíclica e transições ⭐ MVP

**User Story**: As an attendant, I want the system to call the next ticket and record outcomes so that the cycle is fair and closes automatically.

**Why P1**: É o coração da fila cíclica: rodízio, tentativas ponderadas e abertura do próximo ciclo.

**Acceptance Criteria**:
1. WHEN an authenticated user calls the next ticket in a queue with no CALLED ticket THEN the system SHALL select the WAITING ticket with the lowest callsInCycle, then highest cycleWeight, then earliest createdAt, then lowest id. <!-- CYCLE-01 -->
2. WHEN the selected ticket is returned THEN the system SHALL increment callsInCycle, set status CALLED, record calledAt and a cycle call row, and return HTTP 200. <!-- CYCLE-02 -->
3. IF the queue already has a CALLED ticket THEN the system SHALL return HTTP 409 with code `ACTIVE_CALL_EXISTS` and SHALL NOT change any ticket. <!-- CYCLE-03 -->
4. IF the active cycle has no WAITING ticket with callsInCycle below cycleWeight THEN the system SHALL return HTTP 409 with code `NO_TICKET_AVAILABLE` and SHALL NOT change the cycle. <!-- CYCLE-04 -->
5. WHEN a CALLED ticket is completed THEN the system SHALL set status COMPLETED, record finishedAt, and evaluate cycle closure. <!-- CYCLE-05 -->
6. WHEN a CALLED ticket is marked "não atendeu" and callsInCycle is below cycleWeight THEN the system SHALL return it to WAITING; WHEN callsInCycle equals cycleWeight THEN the system SHALL set NO_SHOW. <!-- CYCLE-06 -->
7. WHEN a CALLED ticket is explicitly marked NO_SHOW THEN the system SHALL set NO_SHOW regardless of remaining attempts. <!-- CYCLE-07 -->
8. WHEN a cycle has no WAITING or CALLED tickets THEN the system SHALL close it and create the next ACTIVE cycle with number incremented, moving every NO_SHOW ticket to WAITING with callsInCycle 0 and a new cycleWeight snapshot. <!-- CYCLE-08 -->
9. WHILE a cycle is active the system SHALL call each ticket at most cycleWeight times. <!-- CYCLE-09 -->
10. IF complete, "não atendeu", NO_SHOW, or cancel is requested for a ticket in an invalid status THEN the system SHALL return HTTP 409 with code `INVALID_STATE`. <!-- CYCLE-10 -->
11. WHEN two call-next requests for the same queue run concurrently THEN the system SHALL allow exactly one to create a CALLED ticket and the other SHALL return HTTP 409 `ACTIVE_CALL_EXISTS`. <!-- CYCLE-11 -->

**Independent Test**: Criar três tickets (URGENT, PREFERENTIAL, NORMAL), chamar e concluir todos, registrar "não atendeu" em um, e confirmar que o ciclo fecha e o NO_SHOW volta no ciclo seguinte com tentativas zeradas.

---

### P2: Operação e diagnóstico

**User Story**: As an operator, I want health and transition logs so that I can confirm the API is alive and diagnose a stuck queue.

**Why P2**: Não bloqueia o MVP, mas evita suporte às cegas.

**Acceptance Criteria**:
1. WHEN an authenticated user requests `/actuator/health` THEN the system SHALL return the application and database health status. <!-- OPS-01 -->
2. WHEN a ticket changes state THEN the system SHALL log queue id, ticket code, cycle number, previous status, and new status at INFO without customer personal data. <!-- OPS-02 -->

**Independent Test**: Chamar health e conferir os logs de uma transição de CALLED para COMPLETED.

---

## Edge Cases

Edge cases are usually unwanted-behavior (IF/THEN) or boundary (WHEN) criteria:

- IF a call-next request arrives while another holds the queue lock THEN the system SHALL return 409 `ACTIVE_CALL_EXISTS` for the loser, never two CALLED tickets.
- IF two ticket creations use the same `externalReference` concurrently THEN the unique constraint SHALL keep one ticket and the other request SHALL return the existing ticket.
- IF the last ticket of a cycle is cancelled THEN the system SHALL close the cycle and open the next one, even without any COMPLETED ticket.
- IF a ticket is created after a cycle closes THEN the system SHALL assign it to the new active cycle with a fresh `cycleWeight`.
- IF a queue is deactivated while a ticket is CALLED THEN the system SHALL return 409 and leave the cycle untouched.
- IF a page size greater than 100 is requested THEN the system SHALL return HTTP 400 with a field error.

---

## Requirement Traceability

Each requirement gets a unique ID for tracking across design, tasks, and validation.

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| AUTH-01 | P1: Autenticação | Tasks | In Tasks |
| AUTH-02 | P1: Autenticação | Tasks | In Tasks |
| AUTH-03 | P1: Autenticação | Tasks | In Tasks |
| AUTH-04 | P1: Autenticação | Tasks | In Tasks |
| AUTH-05 | P1: Autenticação | Tasks | In Tasks |
| USER-01 | P1: Usuários | Tasks | In Tasks |
| USER-02 | P1: Usuários | Tasks | In Tasks |
| USER-03 | P1: Usuários | Tasks | In Tasks |
| USER-04 | P1: Usuários | Tasks | In Tasks |
| USER-05 | P1: Usuários | Tasks | In Tasks |
| QUEUE-01 | P1: Filas | Tasks | In Tasks |
| QUEUE-02 | P1: Filas | Tasks | In Tasks |
| QUEUE-03 | P1: Filas | Tasks | In Tasks |
| QUEUE-04 | P1: Filas | Tasks | In Tasks |
| QUEUE-05 | P1: Filas | Tasks | In Tasks |
| QUEUE-06 | P1: Filas | Tasks | In Tasks |
| TICKET-01 | P1: Tickets | Tasks | In Tasks |
| TICKET-02 | P1: Tickets | Tasks | In Tasks |
| TICKET-03 | P1: Tickets | Tasks | In Tasks |
| TICKET-04 | P1: Tickets | Tasks | In Tasks |
| TICKET-05 | P1: Tickets | Tasks | In Tasks |
| TICKET-06 | P1: Tickets | Tasks | In Tasks |
| TICKET-07 | P1: Tickets | Tasks | In Tasks |
| TICKET-08 | P1: Tickets | Tasks | In Tasks |
| CYCLE-01 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-02 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-03 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-04 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-05 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-06 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-07 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-08 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-09 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-10 | P1: Chamada cíclica | Tasks | In Tasks |
| CYCLE-11 | P1: Chamada cíclica | Tasks | In Tasks |
| OPS-01 | P2: Operação | Tasks | In Tasks |
| OPS-02 | P2: Operação | Tasks | In Tasks |

**ID format:** `[CATEGORY]-[NUMBER]` (e.g., `AUTH-01`, `CART-03`, `NOTIF-02`)

**Status values:** Pending → In Design → In Tasks → Implementing → Verified

**Coverage:** 37 total, 37 mapped to tasks, 0 unmapped.

---

## Differences from the source

None - no visual or external binding source was provided. The written request and the user's answers are the only sources.

## Sources

- Conversa com o usuário em 2026-11-08 - respostas às perguntas de escopo, ciclo, prioridade, NO_SHOW, autenticação e concorrência.
- Contexto do projeto (nome do repositório) - indica fila de atendimento assistido por IA; a IA foi explicitamente excluída do MVP.
- No captures - this feature has no screens.

---

## Success Criteria

How we know the feature is successful:

- [ ] Um operador consegue, só com a API, criar fila, criar tickets, chamar, concluir e registrar ausência sem intervenção manual no banco.
- [ ] O ciclo fecha automaticamente e o próximo abre com os NO_SHOW reenfileirados e tentativas zeradas.
- [ ] Duas chamadas concorrentes na mesma fila nunca produzem dois tickets CALLED.
- [ ] `docker compose up` sobe o MySQL e a aplicação inicia com o schema migrado por Flyway.
- [ ] Todos os critérios P1 têm teste automatizado com prova registrada.
