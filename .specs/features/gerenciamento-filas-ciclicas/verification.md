# Gerenciamento de filas cíclicas verification

**Verdict**: PASS
**Proofs**: `proofs.md` at 7467a7d
**Verifier**: independent sub-agent (author != verifier)

## Checks

| Check | Test | Holds |
| --- | --- | --- |
| C1 | `AuthIT.validLoginReturnsJwt` | yes - decodes with the application JwtDecoder and asserts subject, role and expiry |
| C2 | `AuthIT.invalidCredentialsReturnUnauthorized` | yes - wrong password and an unknown email both return 401 INVALID_CREDENTIALS |
| C3 | `SecurityIT.missingOrExpiredJwtReturnsUnauthorized` | yes - missing, expired and foreign-signature tokens all return 401 UNAUTHORIZED |
| C4 | `SecurityIT.insufficientRoleReturnsForbidden` | yes |
| C5 | `AuthIT.passwordIsBcryptAndNeverReturned` | yes - checks `$2` hash at rest and absence of hash/password in the login body |
| C6 | `AdminSeedIT.startupSeedsAdmin` | yes - fresh-context admin exists, is active ADMIN with BCrypt, and re-run does not overwrite |
| C7 | `UserIT.adminCreatesUserWithoutHash` | yes - 201, fields, no hash in body or DB plaintext |
| C8 | `UserIT.duplicateEmailReturnsConflict` | yes - 409 `DUPLICATE_EMAIL` |
| C9 | `UserIT.deactivatedUserCannotLogin` | yes - deactivate then 401 `INVALID_CREDENTIALS` |
| C10 | `UserIT.userListIsPaginatedAndOrdered` | yes - name order, size 2 paging, size 101 -> 400 `INVALID_PAGE_SIZE` |
| C11 | `QueueIT.queueCreationOpensCycleOne` | yes - cycle 1 ACTIVE with the configured 1/2/3 weights and startedAt |
| C12 | `QueueIT.invalidWeightsReturn400` | yes - range, normal>preferential and preferential>urgent all return 400 field errors |
| C13 | `QueueIT.duplicateQueueReturns409` | yes - duplicate name and duplicate prefix both 409 `DUPLICATE_QUEUE` |
| C14 | `QueueIT.queueUpdateKeepsTicketCycleWeight` | yes - weight change 3->7 leaves ticket cycleWeight at 3 |
| C15 | `QueueIT.queueDeactivationGuardsActiveTickets` | yes - WAITING and CALLED -> 409; otherwise inactive + cycle CLOSED with activeQueueId null |
| C16 | `QueueIT.queueStateReturnsCycleCountsAndCalledTicket` | yes - cycle number, all five counts, calledTicket, 404s |
| C17 | `TicketIT.ticketCreationCodesAndSnapshotsWeight` | yes - `PREFIX-NNNN` (0001, 0002), WAITING, calls 0, weight 3/2 |
| C18 | `TicketIT.omittedPriorityDefaultsToNormal` | yes - omitted priority -> NORMAL weight 1, both with and without other fields |
| C19 | `TicketIT.invalidPriorityReturns400` | yes - 400 `INVALID_PRIORITY` |
| C20 | `TicketIT.externalReferenceIsIdempotent` | yes - repeat returns 200 with the same publicId and keeps the original priority |
| C21 | `TicketIT.inactiveQueueRejectsTicket` | yes - 409 `QUEUE_INACTIVE` |
| C22 | `TicketIT.ticketListFiltersAndPaginates` | yes - status, priority and combined filters, paging totals, size 101 -> 400, detail 404 |
| C23 | `TicketIT.priorityChangeIsLockedAfterCall` | yes - CALLED and WAITING-with-calls>0 both return 409 PRIORITY_LOCKED |
| C24 | `TicketIT.cancelEvaluatesCycleClosure` | yes - WAITING and CALLED cancellations both set CANCELLED; closure is asserted |
| C25 | `CycleSchedulerTest.nextSelectionRespectsCallsWeightAndAge` | yes - each tie-break (calls asc, weight desc, createdAt asc, id asc) is isolated with a conflicting ticket |
| C26 | `CallNextIT.callIncrementsAndRecordsTheCall` | yes - CALLED, calls 1, calledAt, one CycleCall with sequence, ticket and attendant |
| C27 | `CallNextIT.activeCallBlocksAnotherCall` | yes - 409 `ACTIVE_CALL_EXISTS`, first unchanged, second untouched, one CycleCall |
| C28 | `CallNextIT.noEligibleTicketReturns409` | yes - exhausted WAITING -> 409 `NO_TICKET_AVAILABLE`, ticket and cycle unchanged |
| C29 | `TicketTransitionIT.completeSetsCompletedAndClosesWhenLast` | yes - COMPLETED + finishedAt, cycle CLOSED, cycle 2 ACTIVE opened |
| C30 | `TicketTransitionIT.noAnswerRetriesUntilWeightThenNoShow` | yes - first miss -> WAITING calls 1; second miss at weight -> rollover proves NO_SHOW (only NO_SHOW is requeued) with calls 0 and weight 2 |
| C31 | `TicketTransitionIT.explicitNoShowSetsNoShow` | yes - NO_SHOW with calls 1 of weight 3, finishedAt set, cycle still ACTIVE |
| C32 | `CycleLifecycleIT.cycleCloseOpensNextAndRequeuesNoShow` | yes - NO_SHOW moved to cycle 2 as WAITING calls 0 with the refreshed weight 2, timestamps cleared |
| C33 | `CycleSchedulerTest.ticketNeverExceedsCycleWeight` | yes - calls==weight excluded, calls==weight-1 included; `applyNoAnswer` uses `<` so the bound is at most weight |
| C34 | `TicketTransitionIT.invalidTransitionReturns409` | yes - complete/no-answer/no-show on WAITING, repeat complete, no-answer and cancel after COMPLETED -> 409 `INVALID_STATE`; unknown id -> 404 |
| C35 | `CallNextConcurrencyIT.concurrentCallsAllowOnlyOneWinner` | yes - exactly 200 + 409 `ACTIVE_CALL_EXISTS`, one CALLED, one WAITING, one CycleCall |
| C36 | `OperationsIT.healthReportsApplicationAndDatabase` | yes - status UP, ping UP and db UP |
| C37 | `OperationsIT.transitionLogCarriesCycleAndStatuses` | yes - events are INFO and carry queue, code, cycle, from/to and no customer name |

## Findings

Probes run at HEAD as a temporary `VerifierProbeIT` (then deleted, tree clean): (1) callsInCycle asc outranks cycleWeight desc in the real HTTP flow - called URGENT, returned it to WAITING with calls 1, then the next call correctly returned NORMAL with calls 0; (2) `externalReference` is scoped per queue - the same reference on two queues created two distinct tickets, while repeating it on the first queue returned the first ticket with 200. Both behaved correctly; the findings below are test-coverage gaps, not observed behaviour failures.

| Finding | Evidence | Fix | Closed |
| --- | --- | --- | --- |
| C1 does not prove the login token is a signed JWT | `AuthIT.validLoginReturnsJwt` only asserts `$.accessToken` is not empty; a random string would pass | decode the returned token with the application `JwtDecoder` and assert subject/role  | 7dd05ed (C1) |
| C2 omits the unknown-email case the criterion names | `AuthIT.invalidCredentialsReturnUnauthorized` posts only a wrong password | add a login with an unregistered email asserting 401 `INVALID_CREDENTIALS`  | 7dd05ed (C2) |
| C3 omits the invalid-signature case the criterion names | `SecurityIT.missingOrExpiredJwtReturnsUnauthorized` covers only absent and expired tokens | send a token signed with a different secret and assert 401 `UNAUTHORIZED`  | 7dd05ed (C3) |
| C12 omits the preferential>urgent ordering error | `QueueIT.invalidWeightsReturn400` uses (0,2,3), (5,3,3) and (1,2,11); no case such as (1,3,2); a missing preferential<=urgent check would stay green | add weightNormal=1, weightPreferential=3, weightUrgent=2 expecting 400 with `fields.weightPreferential`  | 7dd05ed (C12) |
| C23 never exercises WAITING with callsInCycle>0 | `TicketIT.priorityChangeIsLockedAfterCall` blocks via CALLED status; a no-answer below weight leaves status WAITING with calls>0, which the criterion also requires to be `PRIORITY_LOCKED` | call the ticket, record "não atendeu" below the weight, then PATCH priority expecting 409 `PRIORITY_LOCKED`  | 7dd05ed (C23) |
| C24 never cancels a CALLED ticket | `TicketIT.cancelEvaluatesCycleClosure` cancels a WAITING ticket only; a CALLED cancel could be rejected and the test would still pass | call the next ticket then DELETE it, asserting 204 and status CANCELLED  | 7dd05ed (C24) |
| C37 does not assert the log level is INFO | `OperationsIT.transitionLogCarriesCycleAndStatuses` captures events through an INFO-threshold appender but never inspects `ILoggingEvent.getLevel()`; a WARN transition would pass | assert the transition event level equals `Level.INFO`  | 7dd05ed (C37) |
| No test for concurrent ticket creation with the same `externalReference` | Spec Edge Cases requires two concurrent creations with one reference to keep one ticket and return the existing one; only the sequential `TicketIT.externalReferenceIsIdempotent` exists | add a two-thread create test on one queue with the same reference, asserting one stored ticket and one 200/201  | 7dd05ed (concurrent external reference creates one ticket) |
| Queue list page-size cap is never exercised | `QueueIT.queueStateReturnsCycleCountsAndCalledTicket` requests `size=100` but never `size>100`, and `QueueService` has its own pagination validator separate from users/tickets | add `GET /api/v1/queues?size=101` expecting 400 `INVALID_PAGE_SIZE`  | 7dd05ed (C16) |
