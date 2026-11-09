# API de Filas Cíclicas de Atendimento

API REST em Java 21 + Spring Boot 4.1.2 para operar filas de atendimento com ciclos, prioridade
ponderada e tentativas limitadas por ciclo. O MySQL 8.4 sobe em Docker Compose e o schema é aplicado
por Flyway no startup.

## Requisitos

- Java 21
- Docker (Docker Desktop ou equivalente)
- Maven 3.9+ ou o wrapper incluído (`mvnw` / `mvnw.cmd`)

## Subir o banco

```bash
docker compose up -d
```

O serviço `mysql` expõe a porta 3306, cria o banco `fila_ciclica` e tem healthcheck com `mysqladmin
ping`. Para acompanhar os logs:

```bash
docker compose logs -f mysql
```

## Variáveis de ambiente

| Variável | Obrigatória | Padrão | Descrição |
| --- | --- | --- | --- |
| `DB_URL` | não | `jdbc:mysql://localhost:3306/fila_ciclica?...` | URL JDBC do MySQL |
| `DB_USER` | não | `dev` | Usuário do banco |
| `DB_PASSWORD` | não | `dev` | Senha do banco |
| `JWT_SECRET` | sim | valor de desenvolvimento | Segredo HS256 com no mínimo 32 caracteres |
| `ADMIN_EMAIL` | sim | `admin@fila.local` | E-mail do ADMIN criado no startup |
| `ADMIN_PASSWORD` | sim | `admin12345` | Senha do ADMIN inicial (BCrypt) |

Copie `.env.example` para `.env` e ajuste os valores. O Compose lê `.env` automaticamente.

## Rodar a aplicação

```bash
mvn spring-boot:run
```

ou, com o wrapper:

```bash
./mvnw spring-boot:run
```

A API fica em `http://localhost:8080`. O health check está exposto em `/actuator/health`.

## Exemplos de uso

Os exemplos usam `http://localhost:8080` e o ADMIN semeado (`admin@fila.local` / `admin12345`).

### 1. Login

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@fila.local","password":"admin12345"}' \
  | jq -r '.accessToken')
```

### 2. Criar uma fila

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

### 3. Criar tickets

```bash
curl -s -X POST http://localhost:8080/api/v1/queues/$QUEUE_ID/tickets \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"customerName":"Maria Silva","priority":"URGENT"}'
```

`priority` aceita `NORMAL` (padrão), `PREFERENTIAL` e `URGENT`. O `cycleWeight` do ticket é um
snapshot do peso da prioridade no momento da criação.

### 4. Chamar o próximo ticket

```bash
curl -s -X POST http://localhost:8080/api/v1/queues/$QUEUE_ID/calls/next \
  -H "Authorization: Bearer $TOKEN"
```

A seleção usa `callsInCycle` crescente, `cycleWeight` decrescente, `createdAt` e `id`. Só existe uma
chamada ativa por fila; uma segunda chamada retorna 409 `ACTIVE_CALL_EXISTS`.

### 5. Registrar o resultado

```bash
# Atendido
curl -s -X POST http://localhost:8080/api/v1/tickets/$PUBLIC_ID/complete \
  -H "Authorization: Bearer $TOKEN"

# Não atendeu (retorna a WAITING enquanto houver tentativa; NO_SHOW ao esgotar o peso)
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
ciclo abre com número incrementado. Tickets `NO_SHOW` voltam como `WAITING`, com `callsInCycle` zerado
e um novo `cycleWeight`.

### 6. Consultar o estado da fila

```bash
curl -s http://localhost:8080/api/v1/queues/$QUEUE_ID/state \
  -H "Authorization: Bearer $TOKEN"
```

Retorna o número do ciclo ativo, as contagens por status e o ticket `CALLED` atual, se houver.

### 7. Health check

```bash
curl -s http://localhost:8080/actuator/health
```

Resposta esperada: `{"status":"UP","components":{...,"db":{"status":"UP"},...}}`.

## Testes

Os testes de integração sobem MySQL 8.4 via Testcontainers, portanto Docker precisa estar em
execução.

```bash
mvn -q verify
```

## Observabilidade

Cada transição de ticket gera uma linha INFO com `queueId`, `code`, `cycle`, `from` e `to`, sem dados
pessoais do cliente:

```text
Ticket transition queueId=1 code=REC-0001 cycle=1 from=CALLED to=COMPLETED
```
