# Project State

Project: API de gerenciamento de filas cíclicas de atendimento (Java 21 + Spring Boot + MySQL/Docker).

## Decisions

| ID | Date | Status | Decision | Rationale |
| --- | --- | --- | --- | --- |
| AD-001 | 2026-11-08 | active | Stack: Java 21, Spring Boot 4.1.2, Maven (wrapper), MySQL 8.4 LTS, Flyway. | Java 21 e Spring Boot foram pedidos pelo usuário; Maven e Flyway são o padrão mais previsível para o projeto. Spring Boot 4.1.2 é a última versão estável disponível no Maven Central nesta data. |
| AD-002 | 2026-11-08 | active | O MySQL roda em Docker Compose e sobe com `docker compose up`; a API roda fora do container via Maven. | Pedido explícito do usuário para o banco em Docker com um único comando. Manter a API fora do container simplifica o ciclo de desenvolvimento e os testes. |
| AD-003 | 2026-11-08 | active | "Fila cíclica" = ciclos com tentativas ponderadas: cada ticket pode ser chamado até `cycleWeight` vezes por ciclo; uma chamada ativa por fila; ciclo fecha quando não há tickets WAITING nem CALLED. | Decisões do usuário: rodízio circular, peso por prioridade, máximo de tentativas, uma chamada ativa por fila. |
| AD-004 | 2026-11-08 | active | Autenticação JWT HS256 com papéis ADMIN e ATTENDANT; ADMIN inicial criado por seed de ambiente; sem refresh token. | Decisão do usuário por JWT com papéis e seed; refresh token não foi pedido e fica fora do MVP. |
| AD-005 | 2026-11-08 | active | Assistência por IA fica fora do MVP. | Decisão do usuário: "Fora do MVP". O domínio não terá integração com LLM nesta entrega. |
| AD-006 | 2026-11-08 | active | Monólito modular em camadas (api → service → domain → repository), com lock pessimista na linha da fila para operações de chamada/transição. | Uma única fila não pode ter duas chamadas ativas; o lock por fila torna a garantia testável e simples de operar. |
| AD-007 | 2026-11-08 | active | API versionada em `/api/v1`, erros em RFC 9457 ProblemDetail e identificador público de ticket em UUID. | Contrato estável, erros padronizados e IDs públicos que não expõem sequência do banco. |

## Handoff

**Current state**: Specify concluído; Design e Tasks escritos como rascunho para revisão. Nenhum código de implementação foi criado.

**Awaiting**: revisão do usuário em `.specs/features/gerenciamento-filas-ciclicas/` (spec.md, context.md, design.md, tasks.md). As aprovações de spec e tasks ainda não foram dadas.

**Next step after approval**: executar `validate_tasks.py --execute`, implementar as tarefas na ordem das fases, um commit atômico por tarefa.

**Blast radius**: aprovar os artefatos autoriza apenas implementação e commits locais; push, deploy e qualquer operação destrutiva exigem autorização explícita.
