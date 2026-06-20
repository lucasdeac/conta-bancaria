# Modelo de dados — DynamoDB (single-table)

Tabela única `ledger` com três tipos de item, usando o padrão **single-table design**.
Valores monetários são armazenados como **inteiro em *minor units*** (centavos) no tipo `Number`
do DynamoDB, mapeados para `BigDecimal` no domínio — **nunca `double`/`float`** (ver [ADR-0004](adr/0004-money-minor-units.md)).

## Itens

### Conta (Account)

| Atributo | Tipo | Exemplo | Notas |
|---|---|---|---|
| `PK` | S | `ACC#5b19c8b6-...` | partition key |
| `SK` | S | `#META` | sort key fixo |
| `accountId` | S | `5b19c8b6-...` | UUID |
| `owner` | S | `315e3cfe-...` | UUID do titular (PII — não logar) |
| `balanceMinor` | N | `18312` | saldo em centavos (R$ 183,12) |
| `currency` | S | `BRL` | ISO 4217 |
| `status` | S | `ENABLED` | gate de autorização (ver [ADR-0006](adr/0006-account-status-gating.md)) |
| `createdAt` | S | `2021-10-22T...` | derivado do epoch da mensagem |
| `version` | N | `7` | controle de versão (auditoria/optimistic opcional) |

### Transação (Transaction)

| Atributo | Tipo | Exemplo | Notas |
|---|---|---|---|
| `PK` | S | `TXN#8e8ae808-...` | partition key = transactionId (idempotência) |
| `SK` | S | `#META` | sort key fixo |
| `transactionId` | S | `8e8ae808-...` | UUID (vem da URL) |
| `accountId` | S | `5b19c8b6-...` | UUID |
| `type` | S | `CREDIT` / `DEBIT` | |
| `amountMinor` | N | `9707` | valor em centavos |
| `currency` | S | `BRL` | ISO 4217 |
| `status` | S | `PENDING`→`SUCCEEDED`/`FAILED` | claim → resolução |
| `failureReason` | S | `INSUFFICIENT_BALANCE` | só quando FAILED |
| `resultingBalanceMinor` | N | `18312` | saldo após a transação (quando SUCCEEDED) |
| `timestamp` | S | `2025-07-08T15:57:55-03:00` | ISO 8601 |
| `GSI1PK` | S | `ACC#5b19c8b6-...` | índice de extrato |
| `GSI1SK` | S | `2025-07-08T15:57:55-03:00#TXN#8e8...` | ordena por tempo |

## Índices

### GSI1 — extrato bancário

- **GSI1PK** = `ACC#<accountId>`
- **GSI1SK** = `<timestamp>#TXN#<transactionId>`

Permite `Query` do **extrato** por conta, ordenado por tempo, com paginação. Também serve para
consulta histórica. A **consulta de saldo** usa o item da conta direto (`GetItem` por `PK`).

## Padrões de acesso

| # | Operação | Acesso |
|---|---|---|
| 1 | Criar conta (saldo 0) | `PutItem` Account com `attribute_not_exists(PK)` (idempotente) |
| 2 | Claim de transação | `PutItem` Transaction `PENDING` com `attribute_not_exists(PK)` |
| 3 | Aplicar CREDIT | `UpdateItem` Account: `SET balanceMinor = balanceMinor + :v` cond. `attribute_exists AND #status = :enabled` |
| 4 | Aplicar DEBIT | `UpdateItem` Account: `SET balanceMinor = balanceMinor - :v` cond. `attribute_exists AND #status = :enabled AND balanceMinor >= :v` |
| 5 | Resolver transação | `UpdateItem` Transaction → `SUCCEEDED`/`FAILED` |
| 6 | Replay (idempotência) | `GetItem` Transaction por `PK` |
| 7 | Consultar saldo | `GetItem` Account por `PK` |
| 8 | Extrato | `Query` GSI1 por `GSI1PK` |

> Em todos os `UpdateItem` de débito usa-se `ReturnValuesOnConditionCheckFailure: ALL_OLD`
> para classificar o motivo do FAILED (inexistente / desabilitada / saldo insuficiente)
> sem uma leitura adicional.
