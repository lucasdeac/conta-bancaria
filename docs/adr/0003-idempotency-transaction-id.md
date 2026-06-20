# ADR-0003 — Idempotência via transactionId

**Status:** aceito · **Data:** 2026-06-14

## Contexto
- A fila SQS é **Standard** → entrega *at-least-once*, duplicatas e sem ordenação garantida.
- Clientes da API fazem retry. `POST /transactions/{transactionId}` traz o id na URL.

## Decisão
Usar o **`transactionId` como chave de idempotência** (PK do item de transação).
- POST: `PutItem` com `attribute_not_exists(PK)`. Se já existe → `GetItem` e **replay** do
  resultado salvo (mesma resposta).
- Consumer de contas: `PutItem` da conta com `attribute_not_exists(PK)` → redelivery não
  recria nem zera o saldo.

## Motivadores
- Reprocessar a mesma transação nunca dobra o saldo.
- Resposta consistente e determinística para o cliente em retries.
- Tolera duplicatas do SQS sem efeitos colaterais.

## Tradeoffs / consequências
- (+) Segurança contra duplicação; pré-requisito para retry/backoff agressivo.
- (−) Item de transação precisa persistir também os FAILED (para replay correto).
- (−) Estado `PENDING` exige reconciliação se houver crash no meio (ver [ADR-0002](0002-concurrency-conditional-update.md)).
