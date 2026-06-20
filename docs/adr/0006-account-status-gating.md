# ADR-0006 — Gate de autorização por status da conta

**Status:** aceito · **Data:** 2026-06-14

## Contexto
A mensagem de criação traz `status` (o gerador emite sempre `ENABLED`, mas o campo existe).
O enunciado diz que a autorização ocorre em conta "existente", sem detalhar status.

## Decisão
Autorizar apenas contas **`ENABLED`**. Transação em conta com outro status → **FAILED**
(`failureReason = ACCOUNT_DISABLED`), sem alterar o saldo. O gate é aplicado **na própria
`ConditionExpression`** (`#status = :enabled`), mantendo a atomicidade.

## Motivadores
- Comportamento realista para um autorizador financeiro (conta bloqueada não transaciona).
- Custo zero: a checagem entra na condição atômica já existente.

## Tradeoffs / consequências
- (+) Mais seguro e próximo de produção.
- (−) Vai além da letra do enunciado → registrado como premissa ([assumptions.md](../assumptions.md), P4).
- O modelo persiste `status`, então mudar a regra depois é trivial.
