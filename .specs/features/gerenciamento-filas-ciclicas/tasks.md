# Gerenciamento de Filas Cíclicas Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, the walk, check_proofs, the Verifier and the screens sub-agent).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: `.specs/features/gerenciamento-filas-ciclicas/design.md`
**Status**: In Progress
**Approved**: 2026-11-08 - user: "Eu li os arquivos e acredito que esteja tudo bem. Pode implementar o arquivo de tasks e use sub agentes sempre que possível."
**Build**: `mvn -q verify`

---

## Test Coverage Matrix

> Generated from the project's stack and spec. Guidelines found: none (greenfield) - strong defaults applied.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Domain / business logic (scheduler, cycle rules) | unit | All branches; 1:1 to CYCLE ACs; every listed edge case | `src/test/java/com/fila/ciclica/domain/*Test.java` | `mvn -Dtest=*Test test` |
| Controller / API | integration | Every route in scope: happy path + listed edge cases + error paths | `src/test/java/com/fila/ciclica/api/*IT.java` | `mvn -Dit.test=*IT verify` |
| Repository / data access | integration | Key queries, locks, unique constraints, migration | `src/test/java/com/fila/ciclica/repository/*IT.java` | `mvn -Dit.test=*IT verify` |
| Security | integration | Login, 401, 403, seed, password hashing | `src/test/java/com/fila/ciclica/security/*IT.java` | `mvn -Dit.test=*IT verify` |
| Entity / migration / config | none | Build gate only | `src/main/java/com/fila/ciclica/domain/`, `src/main/resources/db/migration/` | build gate only |
| Observability / docs | integration for health/log proof; none for README | Health endpoint and transition log fields | `src/test/java/com/fila/ciclica/observability/*IT.java` | `mvn -Dit.test=*IT verify` |

## Gate Check Commands

> Generated from the project's Maven stack - confirm before Execute.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | After tasks with unit tests only | `mvn test` |
| Full | After tasks with integration/e2e tests | `mvn verify` |
| Build | After config/entity/migration-only tasks | `mvn -q verify` |

---

## Execution Plan

Phases are ordered and run sequentially - each phase completes before the next begins, and tasks within a phase execute in order.

### Phase 1: Foundation

```
T1 → T2
T1 → T3 → T4 → T5
```

### Phase 2: Data and Errors

```
T6 → T7
```

### Phase 3: Security and Users

```
T9 → T10 → T11
T9 → T11
```

### Phase 4: Queues

```
T12 → T13
```

### Phase 5: Tickets

```
T14 → T15
T14 → T16
```

### Phase 6: Cycles

```
T17 → T18 → T19
```

### Phase 7: Hardening and Documentation

```
T20 → T21
```

---

## Task Breakdown

### Phase 1: Foundation

#### T1: Bootstrap do projeto Maven

**What**: Criar o projeto Spring Boot 4.1.2 com Java 21, Maven wrapper, dependências base e classe main.
**Where**: `pom.xml`
**Depends on**: None
**Reuses**: Nenhum (greenfield)
**Requirement**: Base para AUTH-01..OPS-02

**Done when**:

- [x] `pom.xml` declara Java 21, Spring Boot 4.1.2, starters web, validation, data-jpa, security, oauth2-resource-server, actuator, Flyway, MySQL, test e Testcontainers.
- [x] Classe `FilaCiclicaApplication` compila com `@SpringBootApplication`.
- [x] `.gitignore` e Maven wrapper (`mvnw`, `mvnw.cmd`, `.mvn/`) existem.
- [x] `mvn -q -DskipTests package` compila.

**Tests**: none
**Gate**: build
**Commit**: `chore(bootstrap): create Spring Boot Maven project`
**Completed**: 2026-11-08 - b6c17d5

---

#### T2: MySQL em Docker Compose

**What**: Criar o serviço MySQL 8.4 que sobe com `docker compose up`.
**Where**: `compose.yaml`
**Depends on**: T1
**Reuses**: Nenhum
**Requirement**: Base para QUEUE-01..CYCLE-11

**Done when**:

- [x] Serviço `mysql` usa `mysql:8.4`, porta 3306, database `fila_ciclica`, usuário e senha de desenvolvimento.
- [x] Healthcheck `mysqladmin ping` com retries e volume nomeado para persistência.
- [x] `.env.example` documenta as variáveis de ambiente.
- [x] `docker compose config` retorna sem erro.

**Tests**: none
**Gate**: build
**Commit**: `chore(docker): add MySQL compose service`
**Completed**: 2026-11-08 - ea3221a

---

#### T3: Configuração da aplicação

**What**: Configurar datasource, Flyway, JPA, Jackson UTC, Actuator, segredo JWT e validações de startup.
**Where**: `src/main/resources/application.yml`
**Depends on**: T1
**Reuses**: `pom.xml` de T1
**Requirement**: Base para AUTH-01..OPS-02

**Done when**:

- [x] Datasource aponta para as variáveis do Compose (`DB_URL`, `DB_USER`, `DB_PASSWORD`).
- [x] Flyway habilitado; JPA com `ddl-auto=validate` e timezone UTC.
- [x] Actuator expõe `health` e `info`.
- [x] `JWT_SECRET`, `ADMIN_EMAIL` e `ADMIN_PASSWORD` são obrigatórios e validados no startup.
- [x] Aplicação compila e o contexto carrega com o banco disponível.

**Tests**: none
**Gate**: build
**Commit**: `chore(config): configure datasource, flyway and actuator`
**Completed**: 2026-11-08 - 32009c9

---

#### T4: Infraestrutura de testes

**What**: Criar a base Testcontainers MySQL e o reporter de provas dos critérios.
**Where**: `src/test/java/com/fila/ciclica/support/`
**Depends on**: T3
**Reuses**: Configuração de T3
**Requirement**: Base de verificação para todos os critérios

**Done when**:

- [x] `AbstractIntegrationTest` sobe `mysql:8.4` via Testcontainers e injeta datasource com `@DynamicPropertySource`.
- [x] `ProofReporter` implementa `TestWatcher` e imprime `PROOF PASSED: <displayName>` em teste bem-sucedido.
- [x] `ContextLoadIT` verifica que o contexto Spring sobe e que o Flyway aplica o schema.
- [x] Test count: todos os testes de `ContextLoadIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `test(support): add Testcontainers base and proof reporter`
**Completed**: 2026-11-08 - 7f41c78

---

#### T5: Migração Flyway V1

**What**: Criar o schema inicial com usuários, filas, ciclos, tickets e chamadas.
**Where**: `src/main/resources/db/migration/`
**Depends on**: T4
**Reuses**: `AbstractIntegrationTest` de T4
**Requirement**: Base para QUEUE-01..CYCLE-11

**Done when**:

- [x] `V1__init_schema.sql` cria `users`, `queues`, `cycles`, `tickets`, `cycle_calls` com FKs e unique constraints do design.
- [x] `cycles.active_queue_id` tem índice único; `tickets.external_reference` é único por fila.
- [x] `MigrationIT` valida tabelas, colunas e constraints no MySQL 8.4.
- [x] Test count: todos os testes de `MigrationIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(db): add initial Flyway schema`
**Completed**: 2026-11-08 - 6b6cdcc

---

### Phase 2: Data and Errors

#### T6: Entidades e enums JPA

**What**: Mapear as entidades `User`, `Queue`, `Cycle`, `Ticket`, `CycleCall` e os enums do domínio.
**Where**: `src/main/java/com/fila/ciclica/domain/`
**Depends on**: T5
**Reuses**: Schema de T5
**Requirement**: Base para AUTH-01..CYCLE-11

**Done when**:

- [x] Enums `Role`, `Priority`, `TicketStatus`, `CycleStatus` existem com os valores do design.
- [x] Entidades usam `@Version` onde indicado, `Instant` e nomes de coluna do schema.
- [x] Mapeamentos compilam e o contexto valida contra o Flyway com `ddl-auto=validate`.
- [x] Nenhuma lógica de negócio fica nas entidades.

**Tests**: none
**Gate**: build
**Commit**: `feat(domain): add JPA entities and enums`
**Completed**: 2026-11-08 - 1a35e39

---

#### T7: Repositories e queries de lock

**What**: Criar os repositories Spring Data com a query de próximo candidato e o lock pessimista da fila.
**Where**: `src/main/java/com/fila/ciclica/repository/`
**Depends on**: T6
**Reuses**: Entidades de T6
**Requirement**: Base para QUEUE-01..CYCLE-11

**Done when**:

- [x] `QueueRepository.findByIdForUpdate` usa `@Lock(PESSIMISTIC_WRITE)`.
- [x] `TicketRepository.findNextCandidate` ordena por `callsInCycle asc, cycleWeight desc, createdAt asc, id asc`.
- [x] Existem queries para `existsByCycleIdAndStatus`, `findByActiveQueueId` e busca por `externalReference`.
- [x] `RepositoryIT` cobre lock, ordenação e constraints únicas.
- [x] Test count: todos os testes de `RepositoryIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(repository): add JPA repositories and lock queries`
**Completed**: 2026-11-08 - 2d78b93

---

#### T8: Tratamento de erros e validação

**What**: Criar `ApiException`, `GlobalExceptionHandler`, `PageResponse` e o contrato ProblemDetail.
**Where**: `src/main/java/com/fila/ciclica/common/`
**Depends on**: T1
**Reuses**: Spring Web e Bean Validation de T1
**Requirement**: Base para AUTH-02..OPS-02

**Done when**:

- [x] Erros de validação retornam 400 com `code=VALIDATION_ERROR` e `fields`.
- [x] `ApiException` mapeia código, status e detail para ProblemDetail.
- [x] `PageResponse` expõe `content`, `page`, `size`, `totalElements`, `totalPages`.
- [x] `ErrorHandlingTest` usa MockMvc standalone com um controller de fixture e cobre 400, 404, 409 e 500.
- [x] Test count: todos os testes de `ErrorHandlingTest` passam; nenhum teste removido.

**Tests**: unit
**Gate**: quick
**Commit**: `feat(common): add ProblemDetail error handling`
**Completed**: 2026-11-08 - 6749edb

---

### Phase 3: Security and Users

#### T9: Infraestrutura de segurança e JWT

**What**: Configurar Spring Security, encoder BCrypt, emissão e validação de JWT HS256 e regras de papel.
**Where**: `src/main/java/com/fila/ciclica/security/`
**Depends on**: T6
**Reuses**: `User` de T6
**Requirement**: AUTH-03, AUTH-04, AUTH-05

**Done when**:

- [x] `SecurityFilterChain` libera login e health e protege `/api/v1/**`.
- [x] `TokenService` emite JWT com `sub`, `role`, `iat`, `exp` de 3600 s.
- [x] `JwtDecoder` valida assinatura e expiração; `PasswordEncoder` usa BCrypt.
- [x] O startup falha se `JWT_SECRET` tiver menos de 32 caracteres.
- [x] `TokenServiceTest` cobre claims, expiração e assinatura inválida.
- [x] Test count: todos os testes de `TokenServiceTest` passam; nenhum teste removido.

**Tests**: unit
**Gate**: quick
**Commit**: `feat(security): add JWT security infrastructure`
**Completed**: 2026-11-08 - 2dd77c7

---

#### T10: Endpoint de login

**What**: Criar `POST /api/v1/auth/login` com autenticação de credenciais e resposta de token.
**Where**: `src/main/java/com/fila/ciclica/api/auth/`
**Depends on**: T9
**Reuses**: `TokenService` e `PasswordEncoder` de T9
**Requirement**: AUTH-01, AUTH-02, AUTH-05

**Done when**:

- [x] Login válido retorna 200 com `accessToken`, `role` e `expiresIn=3600`.
- [x] Credenciais inválidas retornam 401 `INVALID_CREDENTIALS` sem revelar e-mail.
- [x] Resposta não contém senha nem hash.
- [x] `AuthIT` cobre sucesso, falha e hash BCrypt no banco.
- [x] Test count: todos os testes de `AuthIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(auth): add login endpoint`
**Completed**: 2026-11-08 - 1bd6ca9

---

#### T11: Seed do admin e CRUD de usuários

**What**: Criar o ADMIN inicial e os endpoints de criação, listagem, consulta e ativação/desativação de usuários.
**Where**: `src/main/java/com/fila/ciclica/api/user/`
**Depends on**: T9, T10
**Reuses**: `TokenService`, `PasswordEncoder`, `UserRepository`
**Requirement**: USER-01, USER-02, USER-03, USER-04, USER-05

**Done when**:

- [x] Startup cria ADMIN ativo se `ADMIN_EMAIL` não existir; não sobrescreve usuário existente.
- [x] ADMIN cria usuário e recebe 201 sem hash; e-mail duplicado retorna 409.
- [x] Usuário desativado não consegue autenticar.
- [x] Listagem é paginada, ordenada por nome e limitada a 100.
- [x] `AdminSeedIT`, `UserIT` e `SecurityIT` cobrem seed, CRUD, 401 e 403.
- [x] Test count: todos os testes dessas classes passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(user): add admin seed and user management`
**Completed**: 2026-11-08 - 6e0b67d

---

### Phase 4: Queues

#### T12: Criação, atualização e desativação de filas

**What**: Criar o service e os endpoints de ADMIN para criar, atualizar e desativar filas, incluindo o ciclo 1.
**Where**: `src/main/java/com/fila/ciclica/api/queue/`
**Depends on**: T7, T8, T9
**Reuses**: `QueueRepository`, `CycleRepository`, ProblemDetail e segurança
**Requirement**: QUEUE-01, QUEUE-02, QUEUE-03, QUEUE-04, QUEUE-05

**Done when**:

- [x] Criação persiste a fila e o ciclo 1 ACTIVE na mesma transação.
- [x] Pesos inválidos retornam 400 com fields; nome/prefixo duplicado retorna 409.
- [x] Atualização de pesos não altera `cycleWeight` de tickets já no ciclo.
- [x] Desativação com WAITING/CALLED retorna 409; sem tickets ativos desativa e fecha o ciclo.
- [x] `QueueIT` cobre os cinco critérios de fila.
- [x] Test count: todos os testes de `QueueIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(queue): add queue management`
**Completed**: 2026-11-08 - 3c8a548

---

#### T13: Consulta e estado da fila

**What**: Criar listagem, detalhe e `GET /api/v1/queues/{id}/state` com contagens do ciclo ativo.
**Where**: `src/main/java/com/fila/ciclica/api/queue/`
**Depends on**: T12
**Reuses**: `QueueService` e repositories de T12
**Requirement**: QUEUE-06

**Done when**:

- [x] Detalhe retorna nome, prefixo, pesos, ativo e ciclo ativo.
- [x] Estado retorna `cycleNumber`, contagens por status e o ticket CALLED atual, se houver.
- [x] Filas inexistentes retornam 404 com código específico.
- [x] `QueueIT` cobre o estado com e sem ticket chamado.
- [x] Test count: todos os testes de `QueueIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(queue): add queue state endpoint`
**Completed**: 2026-11-08 - 4a2258b

---

### Phase 5: Tickets

#### T14: Criação de tickets

**What**: Criar o endpoint de criação com código `PREFIX-NNNN`, snapshot de peso, prioridade padrão, idempotência e validação de fila ativa.
**Where**: `src/main/java/com/fila/ciclica/api/ticket/`
**Depends on**: T7, T8, T12
**Reuses**: `QueueRepository`, `TicketRepository`, `CycleScheduler`
**Requirement**: TICKET-01, TICKET-02, TICKET-03, TICKET-04, TICKET-05

**Done when**:

- [x] Criação atribui código, status WAITING, `callsInCycle=0` e `cycleWeight` correto.
- [x] Prioridade omitida vira NORMAL; prioridade inválida retorna 400.
- [x] `externalReference` repetida retorna o ticket existente com 200.
- [x] Fila inativa retorna 409 `QUEUE_INACTIVE`.
- [x] `TicketIT` cobre os cinco critérios de criação.
- [x] Test count: todos os testes de `TicketIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(ticket): add ticket creation`
**Completed**: 2026-11-09 - 14e0a83

---

#### T15: Prioridade e cancelamento de tickets

**What**: Criar atualização de prioridade e cancelamento com validação de estado.
**Where**: `src/main/java/com/fila/ciclica/api/ticket/`
**Depends on**: T14
**Reuses**: `TicketService` de T14
**Requirement**: TICKET-07, TICKET-08

**Done when**:

- [x] Prioridade só muda em WAITING com `callsInCycle=0`; caso contrário 409 `PRIORITY_LOCKED`.
- [x] A mudança recalcula `cycleWeight` do ticket.
- [x] Cancelamento de WAITING/CALLED vira CANCELLED e avalia fechamento do ciclo.
- [x] `TicketIT` cobre prioridade bloqueada e cancelamento.
- [x] Test count: todos os testes de `TicketIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(ticket): add priority change and cancellation`
**Completed**: 2026-11-09 - 16d9342

---

#### T16: Listagem e detalhe de tickets

**What**: Criar listagem paginada com filtros de status/prioridade e detalhe por `publicId`.
**Where**: `src/main/java/com/fila/ciclica/api/ticket/`
**Depends on**: T14
**Reuses**: `TicketRepository` e `PageResponse`
**Requirement**: TICKET-06

**Done when**:

- [x] Listagem filtra por status e prioridade e limita `size` a 100.
- [x] Detalhe por `publicId` retorna 404 para UUID inexistente.
- [x] `TicketIT` cobre filtros, paginação e 400 para `size>100`.
- [x] Test count: todos os testes de `TicketIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(ticket): add ticket listing and detail`
**Completed**: 2026-11-09 - e4a0891

---

### Phase 6: Cycles

#### T17: Scheduler de ciclo

**What**: Implementar a seleção do próximo ticket e o limite de tentativas como código de domínio puro.
**Where**: `src/main/java/com/fila/ciclica/domain/`
**Depends on**: T6
**Reuses**: Entidades e enums de T6
**Requirement**: CYCLE-01, CYCLE-09

**Done when**:

- [ ] `CycleScheduler.selectNext` ordena por `callsInCycle`, `cycleWeight`, `createdAt`, `id`.
- [ ] Tickets com `callsInCycle >= cycleWeight` não são candidatos.
- [ ] `CycleSchedulerTest` cobre empate, prioridade, idade e limite de tentativas.
- [ ] Test count: todos os testes de `CycleSchedulerTest` passam; nenhum teste removido.

**Tests**: unit
**Gate**: quick
**Commit**: `feat(domain): add cycle scheduler`

---

#### T18: Chamada do próximo ticket

**What**: Criar `POST /api/v1/queues/{id}/calls/next` com lock por fila, verificação de chamada ativa e registro da chamada.
**Where**: `src/main/java/com/fila/ciclica/api/call/`
**Depends on**: T12, T14, T17
**Reuses**: `CycleScheduler`, `QueueRepository.findByIdForUpdate`, `TicketRepository`
**Requirement**: CYCLE-02, CYCLE-03, CYCLE-04

**Done when**:

- [ ] Chamada incrementa `callsInCycle`, seta CALLED e grava `CycleCall`.
- [ ] Chamada com CALLED existente retorna 409 `ACTIVE_CALL_EXISTS`.
- [ ] Sem candidato retorna 409 `NO_TICKET_AVAILABLE` sem alterar o ciclo.
- [ ] `CallNextIT` cobre sucesso, chamada ativa e ausência de candidato.
- [ ] Test count: todos os testes de `CallNextIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(call): add call next endpoint`

---

#### T19: Conclusão, ausência e fechamento do ciclo

**What**: Criar complete, no-answer, no-show e cancelamento com fechamento/abertura automática do ciclo.
**Where**: `src/main/java/com/fila/ciclica/api/call/`
**Depends on**: T18
**Reuses**: `CallService`, `CycleScheduler`, `TicketRepository`
**Requirement**: CYCLE-05, CYCLE-06, CYCLE-07, CYCLE-08, CYCLE-10

**Done when**:

- [ ] Complete só a partir de CALLED e seta COMPLETED com `finishedAt`.
- [ ] No-answer devolve a WAITING abaixo do peso e seta NO_SHOW ao esgotar.
- [ ] No-show explícito seta NO_SHOW; transições inválidas retornam 409.
- [ ] Fechamento cria o próximo ciclo e reenfileira NO_SHOW com tentativas zeradas.
- [ ] `TicketTransitionIT` e `CycleLifecycleIT` cobrem os cinco critérios.
- [ ] Test count: todos os testes dessas classes passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(call): add attendance outcomes and cycle rollover`

---

### Phase 7: Hardening and Documentation

#### T20: Suíte E2E e regressão de concorrência

**What**: Criar o fluxo E2E completo e o teste de concorrência de duas chamadas na mesma fila.
**Where**: `src/test/java/com/fila/ciclica/e2e/`
**Depends on**: T19
**Reuses**: `AbstractIntegrationTest`, `ProofReporter`
**Requirement**: CYCLE-11, Success Criteria

**Done when**:

- [ ] `CyclicQueueE2EIT` percorre fila, tickets, chamadas, ausências, fechamento e reabertura.
- [ ] `CallNextConcurrencyIT` dispara duas chamadas simultâneas e obtém exatamente um 200 e um 409.
- [ ] Test count: todos os testes de E2E e concorrência passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `test(e2e): add full cycle and concurrency suites`

---

#### T21: Health, logs de transição e README

**What**: Expor health, registrar transições de estado e documentar execução local e endpoints.
**Where**: `src/main/java/com/fila/ciclica/observability/`
**Depends on**: T20
**Reuses**: Actuator de T3, serviços de T19
**Requirement**: OPS-01, OPS-02

**Done when**:

- [ ] `/actuator/health` retorna status da aplicação e do banco.
- [ ] Cada transição loga queue id, ticket code, cycle number, status anterior e novo, sem dados pessoais.
- [ ] `OperationsIT` cobre health e a linha de log de uma transição.
- [ ] `README.md` documenta `docker compose up`, variáveis de ambiente, `mvn spring-boot:run` e exemplos curl.
- [ ] Test count: todos os testes de `OperationsIT` passam; nenhum teste removido.

**Tests**: integration
**Gate**: full
**Commit**: `feat(ops): add health, transition logs and README`

---

## Checks

**Run auth**: `mvn -Dit.test=AuthIT,SecurityIT,AdminSeedIT,UserIT verify`
**Run queues**: `mvn -Dit.test=QueueIT verify`
**Run tickets**: `mvn -Dit.test=TicketIT verify`
**Run unit**: `mvn -Dtest=CycleSchedulerTest test`
**Run calls**: `mvn -Dit.test=CallNextIT,TicketTransitionIT,CycleLifecycleIT,CyclicQueueE2EIT verify`
**Run concurrency**: `mvn -Dit.test=CallNextConcurrencyIT verify`
**Run ops**: `mvn -Dit.test=OperationsIT verify`

**C1** - Valid credentials return 200 with a JWT, role and `expiresIn=3600` (AUTH-01)
Proof: run auth | PROOF PASSED: C1 valid login returns JWT

**C2** - Invalid credentials return 401 `INVALID_CREDENTIALS` (AUTH-02)
Proof: run auth | PROOF PASSED: C2 invalid credentials return 401

**C3** - Missing, invalid or expired JWT returns 401 `UNAUTHORIZED` (AUTH-03)
Proof: run auth | PROOF PASSED: C3 missing or expired JWT returns 401

**C4** - ATTENDANT on an admin-only endpoint returns 403 `FORBIDDEN` (AUTH-04)
Proof: run auth | PROOF PASSED: C4 insufficient role returns 403

**C5** - Stored password is a BCrypt hash and no response contains it (AUTH-05)
Proof: run auth | PROOF PASSED: C5 password is BCrypt and never returned

**C6** - Startup creates an active ADMIN when the configured email is absent (USER-01)
Proof: run auth | PROOF PASSED: C6 startup seeds admin

**C7** - ADMIN creates a user with 201 and no password hash (USER-02)
Proof: run auth | PROOF PASSED: C7 admin creates user without hash

**C8** - Duplicate email returns 409 `DUPLICATE_EMAIL` (USER-03)
Proof: run auth | PROOF PASSED: C8 duplicate email returns 409

**C9** - Deactivated user cannot log in (USER-04)
Proof: run auth | PROOF PASSED: C9 deactivated user cannot login

**C10** - User list is paginated, ordered by name and capped at 100 (USER-05)
Proof: run auth | PROOF PASSED: C10 user list is paginated and ordered

**C11** - Queue creation persists cycle 1 ACTIVE with the configured weights (QUEUE-01)
Proof: run queues | PROOF PASSED: C11 queue creation opens cycle one

**C12** - Invalid weights return 400 with field errors (QUEUE-02)
Proof: run queues | PROOF PASSED: C12 invalid weights return 400

**C13** - Duplicate name or prefix returns 409 `DUPLICATE_QUEUE` (QUEUE-03)
Proof: run queues | PROOF PASSED: C13 duplicate queue returns 409

**C14** - Queue update keeps the `cycleWeight` snapshot of tickets in the active cycle (QUEUE-04)
Proof: run queues | PROOF PASSED: C14 queue update keeps ticket cycle weight

**C15** - Deactivation with WAITING/CALLED returns 409; otherwise the queue is inactive and the cycle closed (QUEUE-05)
Proof: run queues | PROOF PASSED: C15 queue deactivation guards active tickets

**C16** - Queue state returns cycle number, counts and the CALLED ticket (QUEUE-06)
Proof: run queues | PROOF PASSED: C16 queue state returns cycle counts and called ticket

**C17** - Ticket creation assigns code, WAITING, calls 0 and the priority weight snapshot (TICKET-01)
Proof: run tickets | PROOF PASSED: C17 ticket creation codes and snapshots weight

**C18** - Omitted priority defaults to `NORMAL` (TICKET-02)
Proof: run tickets | PROOF PASSED: C18 omitted priority defaults to normal

**C19** - Invalid priority returns 400 `INVALID_PRIORITY` (TICKET-03)
Proof: run tickets | PROOF PASSED: C19 invalid priority returns 400

**C20** - Repeated `externalReference` returns the existing ticket with 200 (TICKET-04)
Proof: run tickets | PROOF PASSED: C20 external reference is idempotent

**C21** - Inactive queue rejects ticket creation with 409 `QUEUE_INACTIVE` (TICKET-05)
Proof: run tickets | PROOF PASSED: C21 inactive queue rejects ticket

**C22** - Ticket list filters by status/priority and caps page size at 100 (TICKET-06)
Proof: run tickets | PROOF PASSED: C22 ticket list filters and paginates

**C23** - Priority changes only on WAITING with calls 0; otherwise 409 `PRIORITY_LOCKED` (TICKET-07)
Proof: run tickets | PROOF PASSED: C23 priority change is locked after call

**C24** - Cancelling WAITING/CALLED sets CANCELLED and evaluates cycle closure (TICKET-08)
Proof: run tickets | PROOF PASSED: C24 cancel evaluates cycle closure

**C25** - Next selection orders by calls, weight, creation and id (CYCLE-01)
Proof: run unit | PROOF PASSED: C25 next selection respects calls weight and age

**C26** - Calling increments calls, sets CALLED, records `calledAt` and a `CycleCall` (CYCLE-02)
Proof: run calls | PROOF PASSED: C26 call increments and records the call

**C27** - An existing CALLED ticket blocks another call with 409 `ACTIVE_CALL_EXISTS` (CYCLE-03)
Proof: run calls | PROOF PASSED: C27 active call blocks another call

**C28** - No WAITING candidate returns 409 `NO_TICKET_AVAILABLE` without changing the cycle (CYCLE-04)
Proof: run calls | PROOF PASSED: C28 no eligible ticket returns 409

**C29** - Complete from CALLED sets COMPLETED and closes the cycle when it is the last (CYCLE-05)
Proof: run calls | PROOF PASSED: C29 complete sets completed and closes when last

**C30** - "Não atendeu" returns WAITING below the weight and NO_SHOW at the weight (CYCLE-06)
Proof: run calls | PROOF PASSED: C30 no answer retries until weight then no show

**C31** - Explicit NO_SHOW sets NO_SHOW regardless of remaining attempts (CYCLE-07)
Proof: run calls | PROOF PASSED: C31 explicit no show sets no show

**C32** - Cycle closure creates the next cycle and requeues NO_SHOW with calls 0 and a new weight (CYCLE-08)
Proof: run calls | PROOF PASSED: C32 cycle close opens next and requeues no show

**C33** - A ticket is never called more than `cycleWeight` times in a cycle (CYCLE-09)
Proof: run unit | PROOF PASSED: C33 ticket never exceeds cycle weight

**C34** - Invalid complete/no-answer/no-show/cancel returns 409 `INVALID_STATE` (CYCLE-10)
Proof: run calls | PROOF PASSED: C34 invalid transition returns 409

**C35** - Two concurrent call-next requests produce exactly one 200 and one 409 (CYCLE-11)
Proof: run concurrency | PROOF PASSED: C35 concurrent calls allow only one winner

**C36** - Health reports application and database status (OPS-01)
Proof: run ops | PROOF PASSED: C36 health reports application and database

**C37** - A state transition log carries queue, ticket code, cycle and both statuses (OPS-02)
Proof: run ops | PROOF PASSED: C37 transition log carries cycle and statuses

---

## Handoff

The feature's live state, written as it happens - a compaction gives no warning.

**Settled mid-build:** Build gate re-run after T1: `mvn -q verify` green (2 ITs).

**Gate note:** `validate_tasks.py --execute` reported the Build line red before T1 because the repository was empty (no `pom.xml`). T1 is the bootstrap task that creates the build; the gate is re-run immediately after T1 and before T2.

**Dispatched:** Batch 1 (T1-T5) - 2026-11-08 - sent to a general worker; returned 2026-11-08.
Batch 2 (T6-T11) - 2026-11-08 - sent to a general worker; returned 2026-11-08 (T6-T11 done, C1-C10 green).
Batch 3 (T12-T16) - 2026-11-08 - sent to a general worker; subagent hit the free usage limit after T13. T14-T16 implemented directly and verified 2026-11-09 (C11-C24 green).

**Walked:** none - the API has no screen; the independent tests are executed during Execute.

---

## Phase Execution Map

Phases run in sequence; tasks within a phase run in order.

| Phase | Tasks | Batch |
| --- | --- | --- |
| 1 - Foundation | T1, T2, T3, T4, T5 | Batch 1 |
| 2 - Data and Errors | T6, T7, T8 | Batch 2 |
| 3 - Security and Users | T9, T10, T11 | Batch 2 |
| 4 - Queues | T12, T13 | Batch 3 |
| 5 - Tickets | T14, T15, T16 | Batch 3 |
| 6 - Cycles | T17, T18, T19 | Batch 4 |
| 7 - Hardening | T20, T21 | Batch 4 |

Total: 21 tasks. At Execute, this packs into 4 batches (5, 6, 5, 5 tasks) and the sub-agent offer must be presented before the first batch.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Bootstrap Maven | 1 build descriptor + main class | ✅ Granular |
| T2: Compose MySQL | 1 compose service | ✅ Granular |
| T3: Application config | 1 config file | ✅ Granular |
| T4: Test infrastructure | 1 support package (base + reporter) | ✅ Granular |
| T5: Flyway V1 | 1 migration | ✅ Granular |
| T6: Entities and enums | 1 domain package (5 entities + 4 enums) | ⚠️ Cohesive data model |
| T7: Repositories | 1 repository package | ✅ Granular |
| T8: Error handling | 1 common package | ✅ Granular |
| T9: Security infrastructure | 1 security package | ✅ Granular |
| T10: Login endpoint | 1 endpoint | ✅ Granular |
| T11: Seed + user CRUD | 1 user package | ⚠️ Cohesive user module |
| T12: Queue management | 1 queue module (3 endpoints) | ⚠️ Cohesive queue module |
| T13: Queue state | 1 endpoint read model | ✅ Granular |
| T14: Ticket creation | 1 endpoint + service path | ✅ Granular |
| T15: Priority + cancel | 2 related ticket transitions | ⚠️ Cohesive transitions |
| T16: Ticket list/detail | 2 read endpoints | ✅ Granular |
| T17: Cycle scheduler | 1 domain class | ✅ Granular |
| T18: Call next | 1 endpoint + lock | ✅ Granular |
| T19: Outcomes + rollover | 4 related transitions + cycle close | ⚠️ Cohesive cycle lifecycle |
| T20: E2E + concurrency | 2 test suites | ✅ Test-only deliverable |
| T21: Health + logs + README | 1 observability package + docs | ⚠️ Cohesive operations |

Cohesive multi-file tasks are kept together because splitting them would produce untestable code or duplicate wiring; the gates still require their tests in the same task.

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | - | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T1 | T1 → T3 | ✅ Match |
| T4 | T3 | T3 → T4 | ✅ Match |
| T5 | T4 | T4 → T5 | ✅ Match |
| T6 | T5 (cross-phase) | - | ✅ Backward dependency |
| T7 | T6 | T6 → T7 | ✅ Match |
| T8 | T1 (cross-phase) | - | ✅ Backward dependency |
| T9 | T6 (cross-phase) | - | ✅ Backward dependency |
| T10 | T9 | T9 → T10 | ✅ Match |
| T11 | T9, T10 | T9 → T11, T9 → T10 → T11 | ✅ Match |
| T12 | T7, T8, T9 (cross-phase) | - | ✅ Backward dependency |
| T13 | T12 | T12 → T13 | ✅ Match |
| T14 | T7, T8, T12 (cross-phase) | - | ✅ Backward dependency |
| T15 | T14 | T14 → T15 | ✅ Match |
| T16 | T14 | T14 → T16 | ✅ Match |
| T17 | T6 (cross-phase) | - | ✅ Backward dependency |
| T18 | T12, T14, T17 (T17 same phase) | T17 → T18 | ✅ Match |
| T19 | T18 | T18 → T19 | ✅ Match |
| T20 | T19 (cross-phase) | - | ✅ Backward dependency |
| T21 | T20 | T20 → T21 | ✅ Match |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Build/config | none | none | ✅ OK |
| T2 | Compose/config | none | none | ✅ OK |
| T3 | Config | none | none | ✅ OK |
| T4 | Test support | integration | integration | ✅ OK |
| T5 | Migration | integration | integration | ✅ OK |
| T6 | Entity | none | none | ✅ OK |
| T7 | Repository | integration | integration | ✅ OK |
| T8 | Common/controller advice | unit | unit | ✅ OK |
| T9 | Security | unit | unit | ✅ OK |
| T10 | Controller/API | integration | integration | ✅ OK |
| T11 | Controller/API | integration | integration | ✅ OK |
| T12 | Controller/API | integration | integration | ✅ OK |
| T13 | Controller/API | integration | integration | ✅ OK |
| T14 | Controller/API | integration | integration | ✅ OK |
| T15 | Controller/API | integration | integration | ✅ OK |
| T16 | Controller/API | integration | integration | ✅ OK |
| T17 | Domain | unit | unit | ✅ OK |
| T18 | Controller/API | integration | integration | ✅ OK |
| T19 | Controller/API | integration | integration | ✅ OK |
| T20 | E2E tests | integration | integration | ✅ OK |
| T21 | Observability | integration | integration | ✅ OK |

---

## Task Verification Standards

Every task follows the `Done when` + `Tests` + `Gate` fields above. Each `Done when` is binary and names the gate command. Each task includes its tests in the same task; no test deferral. One atomic commit per task with the planned message.
