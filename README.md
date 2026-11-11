# API de Filas Cíclicas de Atendimento

API REST em **Java 21 + Spring Boot 4.1.2** para operar filas de atendimento com ciclos,
prioridade ponderada e tentativas limitadas por ciclo. O MySQL 8.4 sobe em Docker Compose e o
schema é aplicado pelo Flyway no startup.

## Stack

- Java 21
- Spring Boot 4.1.2 (Web MVC, Validation, Data JPA, Security, Actuator)
- Spring Security com JWT HS256 e papéis `ADMIN` / `ATTENDANT`
- MySQL 8.4 em Docker Compose
- Flyway para migrações
- Maven 3.9+ (wrapper incluído)
- Testcontainers + JUnit 5 para testes de integração

## Estrutura do projeto

```text
src/main/java/com/fila/ciclica/
  api/auth/          login e emissão de JWT
  api/user/          CRUD administrativo de usuários e seed do ADMIN
  api/queue/         filas, estado do ciclo e desativação
  api/ticket/        criação, listagem, prioridade e cancelamento
  api/call/          chamada do próximo, conclusão, ausência e rollover
  common/            exceções, ProblemDetail e paginação
  config/            propriedades da aplicação
  domain/            entidades, enums, CycleScheduler e ciclo de vida
  observability/     log de transições
  repository/        repositórios Spring Data JPA
  security/          configuração JWT e handlers 401/403
src/main/resources/db/migration/   migrations Flyway
src/test/java/com/fila/ciclica/    testes unitários e de integração
.specs/                            spec, design, tasks, provas e verificação (TLC)
compose.yaml                       MySQL 8.4
```

## Requisitos

- Java 21
- Docker (Docker Desktop ou equivalente)
- Maven 3.9+ ou o wrapper incluído (`mvnw` / `mvnw.cmd`)

## Como rodar

### 1. Configure o ambiente

```bash
cp .env.example .env
```

Ajuste o `.env` se necessário. Para desenvolvimento local os valores padrão funcionam.

### 2. Suba o MySQL

```bash
docker compose up -d
docker compose ps
```

O serviço fica `healthy` em alguns segundos. Logs:

```bash
docker compose logs -f mysql
```

### 3. Rode a aplicação

```bash
mvn spring-boot:run
```

ou com o wrapper:

```bash
./mvnw spring-boot:run
# Windows: mvnw.cmd spring-boot:run
```

A API fica em `http://localhost:8080` e o health check em
`http://localhost:8080/actuator/health`.

No startup, um usuário ADMIN é criado caso ainda não exista:

| E-mail | Senha |
| --- | --- |
| `admin@fila.local` | `admin12345` |

> Troque `JWT_SECRET` e a senha do ADMIN em qualquer ambiente publicado.

### Porta 3306 ocupada

O Compose publica o MySQL na porta definida por `MYSQL_PORT` (padrão `3306`). Se já houver um
MySQL local nessa porta, use outra porta no `.env` e ajuste o `DB_URL` da aplicação:

```env
MYSQL_PORT=3307
DB_URL=jdbc:mysql://localhost:3307/fila_ciclica?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

Para derrubar os serviços:

```bash
docker compose down
# apaga também os dados do volume:
docker compose down -v
```

## Variáveis de ambiente

| Variável | Padrão | Descrição |
| --- | --- | --- |
| `MYSQL_DATABASE` | `fila_ciclica` | Banco criado pelo container |
| `MYSQL_USER` | `dev` | Usuário criado pelo container |
| `MYSQL_PASSWORD` | `dev` | Senha do usuário |
| `MYSQL_ROOT_PASSWORD` | `root` | Senha do root do MySQL |
| `MYSQL_PORT` | `3306` | Porta do host publicada pelo Compose |
| `DB_URL` | `jdbc:mysql://localhost:3306/fila_ciclica?...` | URL JDBC usada pela aplicação |
| `DB_USER` | `dev` | Usuário do datasource |
| `DB_PASSWORD` | `dev` | Senha do datasource |
| `JWT_SECRET` | segredo de desenvolvimento | Segredo HS256 com no mínimo 32 caracteres |
| `ADMIN_EMAIL` | `admin@fila.local` | E-mail do ADMIN semeado |
| `ADMIN_PASSWORD` | `admin12345` | Senha do ADMIN semeado |

> O Compose lê `.env` automaticamente. A aplicação lê variáveis de ambiente do processo; ao rodar
> fora do Compose, garanta que `DB_URL` aponte para `MYSQL_PORT` quando ele não for `3306`.

## Perfis e permissões

| Recurso | ADMIN | ATTENDANT | Sem token |
| --- | --- | --- | --- |
| Login | público | público | público |
| Usuários | sim | não (403) | 401 |
| Criar/editar/desativar fila | sim | não (403) | 401 |
| Consultar filas, tickets e estado | sim | sim | 401 |
| Criar ticket, chamar, concluir, ausência, prioridade e cancelar | sim | sim | 401 |
| Health | público | público | público |

## Endpoints

Base: `/api/v1`. Listagens usam `page` (base 0) e `size` (máximo 100).

### Autenticação

| Método | Caminho | Descrição |
| --- | --- | --- |
| POST | `/auth/login` | Retorna `accessToken`, `role` e `expiresIn` |

### Usuários (ADMIN)

| Método | Caminho | Descrição |
| --- | --- | --- |
| POST | `/users` | Cria usuário; `role` omitida vira `ATTENDANT` |
| GET | `/users?page=0&size=20` | Lista paginada ordenada por nome |
| GET | `/users/{id}` | Detalhe |
| PATCH | `/users/{id}/status` | Ativa/desativa com `{"active": true|false}` |

### Filas

| Método | Caminho | Perfil | Descrição |
| --- | --- | --- | --- |
| POST | `/queues` | ADMIN | Cria fila e o ciclo 1 `ACTIVE` |
| GET | `/queues?active=&page=0&size=20` | autenticado | Lista paginada |
| GET | `/queues/{id}` | autenticado | Detalhe e ciclo ativo |
| PUT | `/queues/{id}` | ADMIN | Atualiza nome, descrição e pesos (prefixo imutável) |
| DELETE | `/queues/{id}` | ADMIN | Desativa; 409 se houver `WAITING`/`CALLED` |
| GET | `/queues/{id}/state` | autenticado | Ciclo ativo, contagens e ticket `CALLED` |

### Tickets

| Método | Caminho | Perfil | Descrição |
| --- | --- | --- | --- |
| POST | `/queues/{queueId}/tickets` | ADMIN/ATTENDANT | Cria ticket `PREFIX-NNNN` |
| GET | `/queues/{queueId}/tickets?status=&priority=&page=0&size=20` | autenticado | Lista filtrada |
| GET | `/tickets/{publicId}` | autenticado | Detalhe por UUID |
| PATCH | `/tickets/{publicId}/priority` | ADMIN/ATTENDANT | Troca prioridade e recalcula `cycleWeight` |
| DELETE | `/tickets/{publicId}` | ADMIN/ATTENDANT | Cancela `WAITING`/`CALLED` |

### Chamadas e atendimento

| Método | Caminho | Descrição |
| --- | --- | --- |
| POST | `/queues/{queueId}/calls/next` | Chama o próximo ticket elegível |
| POST | `/tickets/{publicId}/complete` | Conclui um ticket `CALLED` |
| POST | `/tickets/{publicId}/no-answer` | Volta a `WAITING` abaixo do peso; `NO_SHOW` ao esgotar |
| POST | `/tickets/{publicId}/no-show` | Marca `NO_SHOW` independentemente das tentativas |

### Operação

| Método | Caminho | Descrição |
| --- | --- | --- |
| GET | `/actuator/health` | Saúde da aplicação e do banco (público) |

## Exemplos com curl

Os exemplos usam `http://localhost:8080` e o ADMIN semeado.

### Login

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@fila.local","password":"admin12345"}' \
  | jq -r '.accessToken')
```

Sem `jq`, copie o `accessToken` da resposta:

```json
{
  "accessToken": "eyJ...",
  "role": "ADMIN",
  "expiresIn": 3600
}
```

### Criar uma fila

```bash
curl -s -X POST http://localhost:8080/api/v1/queues \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Recepção",
    "prefix": "REC",
    "description": "Atendimento presencial",
    "weightNormal": 1,
    "weightPreferential": 2,
    "weightUrgent": 3
  }'
```

Guarde o `id` retornado como `QUEUE_ID`. A fila nasce com o ciclo número 1 `ACTIVE`.

### Criar tickets

```bash
curl -s -X POST http://localhost:8080/api/v1/queues/$QUEUE_ID/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "customerName": "Maria Silva",
    "priority": "URGENT",
    "externalReference": "pedido-123"
  }'
```

- `priority` aceita `NORMAL` (padrão), `PREFERENTIAL` e `URGENT`.
- `externalReference` é opcional e única por fila. Repetir a referência retorna o ticket existente
  com HTTP 200 em vez de criar duplicata.
- `cycleWeight` é um snapshot do peso da prioridade no momento da criação.

### Listar e consultar tickets

```bash
curl -s "http://localhost:8080/api/v1/queues/$QUEUE_ID/tickets?status=WAITING&priority=URGENT&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"

curl -s http://localhost:8080/api/v1/tickets/$PUBLIC_ID \
  -H "Authorization: Bearer $TOKEN"
```

### Alterar prioridade

```bash
curl -s -X PATCH http://localhost:8080/api/v1/tickets/$PUBLIC_ID/priority \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"priority":"PREFERENTIAL"}'
```

Só é permitido em ticket `WAITING` com `callsInCycle = 0`; caso contrário retorna 409
`PRIORITY_LOCKED`.

### Chamar o próximo ticket

```bash
curl -s -X POST http://localhost:8080/api/v1/queues/$QUEUE_ID/calls/next \
  -H "Authorization: Bearer $TOKEN"
```

A seleção usa `callsInCycle` crescente, `cycleWeight` decrescente, `createdAt` e `id`. Só existe uma
chamada ativa por fila; uma segunda chamada retorna 409 `ACTIVE_CALL_EXISTS`.

### Registrar o resultado

```bash
# Atendido
curl -s -X POST http://localhost:8080/api/v1/tickets/$PUBLIC_ID/complete \
  -H "Authorization: Bearer $TOKEN"

# Não atendeu (volta a WAITING enquanto houver tentativa; NO_SHOW ao esgotar o peso)
curl -s -X POST http://localhost:8080/api/v1/tickets/$PUBLIC_ID/no-answer \
  -H "Authorization: Bearer $TOKEN"

# Ausência explícita (NO_SHOW independente das tentativas restantes)
curl -s -X POST http://localhost:8080/api/v1/tickets/$PUBLIC_ID/no-show \
  -H "Authorization: Bearer $TOKEN"

# Cancelar um ticket WAITING ou CALLED
curl -s -X DELETE http://localhost:8080/api/v1/tickets/$PUBLIC_ID \
  -H "Authorization: Bearer $TOKEN"
```

Quando o ciclo não tem mais tickets `WAITING` nem `CALLED`, ele fecha automaticamente e o próximo
ciclo abre com número incrementado. Tickets `NO_SHOW` voltam como `WAITING`, com `callsInCycle`
zerado e um novo `cycleWeight`.

### Consultar o estado da fila

```bash
curl -s http://localhost:8080/api/v1/queues/$QUEUE_ID/state \
  -H "Authorization: Bearer $TOKEN"
```

Retorna o número do ciclo ativo, as contagens por status e o ticket `CALLED` atual, se houver.

### Criar um atendente

```bash
curl -s -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Atendente",
    "email": "atendente@fila.local",
    "password": "senha12345",
    "role": "ATTENDANT"
  }'
```

### Health check

```bash
curl -s http://localhost:8080/actuator/health
```

Resposta esperada:

```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

## Formato de erro

Erros seguem RFC 9457 (`ProblemDetail`), com `code` e, em validações, `fields`:

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Queue has waiting or called tickets and cannot be deactivated",
  "instance": "/api/v1/queues/1",
  "code": "QUEUE_HAS_ACTIVE_TICKETS"
}
```

Códigos comuns: `VALIDATION_ERROR`, `INVALID_PRIORITY`, `INVALID_STATUS`,
`INVALID_PAGE_SIZE`, `QUEUE_NOT_FOUND`, `TICKET_NOT_FOUND`, `DUPLICATE_QUEUE`,
`DUPLICATE_EMAIL`, `QUEUE_INACTIVE`, `QUEUE_HAS_ACTIVE_TICKETS`, `ACTIVE_CALL_EXISTS`,
`NO_TICKET_AVAILABLE`, `PRIORITY_LOCKED`, `INVALID_STATE`, `UNAUTHORIZED`, `FORBIDDEN`.

## Regras de negócio

- Cada fila tem no máximo um ciclo `ACTIVE`, garantido por índice único em `cycles.active_queue_id`.
- Cada fila tem no máximo um ticket `CALLED` por ciclo.
- O peso da prioridade é o número máximo de tentativas no ciclo (`cycleWeight`).
- `no-answer` devolve o ticket a `WAITING` enquanto `callsInCycle < cycleWeight`; na última
  tentativa ele vira `NO_SHOW`.
- Ao fechar um ciclo, os `NO_SHOW` são reenfileirados no próximo como `WAITING`, com
  `callsInCycle = 0` e novo snapshot de peso.
- A criação de ticket usa lock pessimista na fila para gerar códigos sequenciais sem duplicidade.

## Testes

Os testes de integração sobem MySQL 8.4 via Testcontainers; Docker precisa estar em execução.

```bash
mvn -q verify
```

O build cobre regras de domínio, segurança/JWT, usuários, filas, tickets, ciclos, rollover,
concorrência e operação. A suíte atual tem 53 testes (10 unitários + 43 de integração).

## Observabilidade

Cada transição de ticket gera uma linha INFO com `queueId`, `code`, `cycle`, `from` e `to`, sem dados
pessoais do cliente:

```text
Ticket transition queueId=1 code=REC-0001 cycle=1 from=CALLED to=COMPLETED
```

## Artefatos de especificação

O diretório `.specs/` mantém a spec, o design, o plano de tarefas, as provas de execução e a
verificação independente da feature `gerenciamento-filas-ciclicas`.

## Segurança em produção

- Defina `JWT_SECRET` com pelo menos 32 caracteres aleatórios.
- Troque `ADMIN_PASSWORD` e remova credenciais padrão.
- Restrinja a exposição do MySQL e use TLS conforme o ambiente.
- Considere expor apenas `/actuator/health` e proteger os demais endpoints do Actuator.
