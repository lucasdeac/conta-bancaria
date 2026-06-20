# ADR-0002 — Concorrência: claim + update condicional atômico

**Status:** aceito · **Data:** 2026-06-14

## Contexto
Transações concorrentes na mesma conta não podem causar *lost update* nem saldo negativo.
Débito que estouraria o saldo deve ser recusado **sem alterar o saldo**.

## Decisão
Processo em dois passos:
1. **Claim de idempotência:** `PutItem` da transação em `PENDING` com `attribute_not_exists(PK)`.
2. **Update condicional atômico** do saldo:
   ```
   UpdateItem Account
   SET balanceMinor = balanceMinor - :v          # DEBIT (ou + :v para CREDIT)
   ConditionExpression: attribute_exists(PK)
                        AND #status = :enabled
                        AND balanceMinor >= :v     # só DEBIT
   ReturnValuesOnConditionCheckFailure: ALL_OLD
   ```
3. Resolver a transação para `SUCCEEDED` (com saldo resultante) ou `FAILED` (com motivo).

## Motivadores
- A `ConditionExpression` torna a verificação de saldo **atômica com a escrita** → sem race,
  sem lock, sem read-modify-write.
- `ALL_OLD` no erro permite classificar o motivo do FAILED (inexistente / desabilitada /
  saldo insuficiente) **sem leitura adicional**.
- O claim garante idempotência (ver [ADR-0003](0003-idempotency-transaction-id.md)).

## Tradeoffs / consequências
- (+) Máximo throughput, correção sob concorrência, sem locks.
- (−) Duas escritas no caminho feliz (claim + saldo + resolução). Mitigado: são `O(1)`.
- (−) Crash entre claim e resolução deixa transação `PENDING` → reconciliável (determinístico
  a partir do input; um job/retry pode re-dirigir).

## Alternativas consideradas
- **`TransactWriteItems` (ACID multi-item):** elegante no caminho feliz, mas o rollback impede
  **persistir a transação FAILED** quando a condição de saldo falha. Descartado por isso.
- **Optimistic lock (`version`) + retry:** ótimo didaticamente, mas degrada sob alta contenção
  na mesma conta (muitos retries).
- **Pessimistic lock:** serializa por conta, menor throughput. Não idiomático em DynamoDB.
