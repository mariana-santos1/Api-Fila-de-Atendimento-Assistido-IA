# Gerenciamento de Filas Cíclicas Context

**Gathered:** 2026-11-08
**Spec:** `.specs/features/gerenciamento-filas-ciclicas/spec.md`
**Status:** Ready for design

---

## Feature Boundary

API REST autenticada para administrar filas de atendimento que funcionam em ciclos. Cada fila tem um ciclo ativo, tickets com prioridade e um número máximo de tentativas de chamada por ciclo. O sistema chama o próximo ticket em rodízio, registra conclusão, ausência e desistência, e abre o próximo ciclo automaticamente.

A assistência por IA, embora esteja no nome do projeto, **não faz parte desta entrega**.

---

## Implementation Decisions

### Semântica da fila cíclica

- Decisão do usuário: **"Rodízio circular"** — a fila gira em ciclos e o ciclo recomeça ao final.
- Decisão do usuário: **"Todos chamados 1x"** — o ciclo fecha quando todo ticket elegível foi chamado ao menos uma vez; tickets não concluídos continuam para o próximo ciclo.
- Decisão do usuário: **"Peso por prioridade"** — tickets de maior prioridade podem ser chamados mais de uma vez no mesmo ciclo.
- Decisão do usuário: **"Máximo de tentativas"** — o peso é o máximo de chamadas por ciclo. O atendente registra "não atendeu"; o ticket volta a WAITING enquanto houver tentativa disponível; ao esgotar, vira NO_SHOW e volta no próximo ciclo.
- Decisão do usuário: **"Uma chamada ativa por fila"** — chamar o próximo com outro ticket CALLED retorna 409 até concluir, registrar "não atendeu" ou NO_SHOW.

### Prioridade e ciclo

- Prioridades: `NORMAL`, `PREFERENTIAL`, `URGENT`.
- Pesos padrão por fila: NORMAL = 1, PREFERENTIAL = 2, URGENT = 3; configuráveis de 1 a 10, desde que NORMAL ≤ PREFERENTIAL ≤ URGENT.
- O peso é copiado para o ticket (`cycleWeight`) quando ele entra no ciclo; mudanças futuras na fila não alteram tickets já no ciclo.
- Ordem de chamada: menor `callsInCycle`, depois maior `cycleWeight`, depois `createdAt`, depois `id`.

### Escopo funcional do MVP

- Decisão do usuário: **"Prioridade, Chamar próximo + concluir, CRUD de filas e tickets"**.
- O CRUD de filas e tickets inclui leitura paginada e cancelamento.
- Histórico/auditoria não foi selecionado como capacidade de produto; o sistema guarda registros internos de chamada (`cycle_calls`) apenas para operar o ciclo, sem endpoints de histórico.

### Autenticação e usuários

- Decisão do usuário: **"JWT com papéis"**.
- Decisão do usuário: **"Seed admin + CRUD usuários"** — ADMIN inicial vem de variáveis de ambiente; ADMIN cria e gerencia usuários.
- Papéis: `ADMIN` (filas, usuários, tudo) e `ATTENDANT` (tickets, chamadas, leitura).
- Sem refresh token no MVP.

### Assistência por IA

- Decisão do usuário: **"Fora do MVP"**.

### Agent's Discretion

- Estrutura de pacotes, nomes de classes, versões exatas de dependências, formato dos DTOs e configuração de testes ficam a cargo do agente.
- Maven como build tool, Spring Boot 4.1.2, MySQL 8.4 LTS, Flyway, Spring Data JPA, Spring Security com JWT Nimbus e Testcontainers.
- Formato do código do ticket (`PREFIX-NNNN`), paginação padrão (20, máximo 100), expiração do JWT (3600 s) e detalhes do ProblemDetail.
- Uso de lock pessimista na linha da fila para serializar chamadas.

---

## Specific References

- O usuário pediu explicitamente: Java 21, Spring Boot, MySQL em Docker que sobe com `docker compose up`, e planejamento via TLC-spec-driven antes de qualquer implementação.
- O usuário pediu para **não implementar de imediato**: os artefatos TLC devem ser revisados primeiro.

---

## Deferred Ideas

- Integração real com LLM para resumir, priorizar ou sugerir atendimento.
- Múltiplas posições de atendimento e chamadas simultâneas por fila.
- Painel/histórico de atendimentos e métricas de tempo de espera.
- Notificações em tempo real (WebSocket/SSE) e integração com painel físico.
- Refresh token, recuperação de senha, rate limiting e multi-tenancy.
- Interface web/mobile.
- CI/CD e deploy em produção.
