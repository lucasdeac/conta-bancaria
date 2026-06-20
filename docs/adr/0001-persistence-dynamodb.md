# ADR-0001 — Persistência em DynamoDB

**Status:** aceito · **Data:** 2026-06-14

## Contexto
Autorizador financeiro de alta volumetria com requisitos de consistência forte no saldo,
idempotência e disponibilidade. ~100k contas no setup local, criação contínua em produção.

## Decisão
Usar **DynamoDB** como armazenamento principal.

## Motivadores
- **Conditional writes nativos** (`ConditionExpression`) → update condicional atômico do saldo
  sem locks (ver [ADR-0002](0002-concurrency-conditional-update.md)).
- Escala horizontal e latência single-digit-ms previsível sob alta volumetria.
- Modelo on-demand absorve rajadas (ex.: rajada de 100k do gerador).
- Alinha com o tema AWS do desafio.

## Tradeoffs / consequências
- (+) Consistência atômica por item, throughput alto, baixa latência.
- (−) Sem JOINs/transações relacionais ricas; modelagem orientada a padrões de acesso
  (single-table — ver [ADR-0005](0005-single-table-design.md)).
- (−) Operações multi-item exigem `TransactWriteItems` (custo/limites) — evitado pelo design
  de claim em 2 passos ([ADR-0002](0002-concurrency-conditional-update.md)).

## Alternativas consideradas
- **PostgreSQL/MySQL:** ACID rico, `SELECT ... FOR UPDATE`/optimistic lock. Excelente para ledger,
  mas escala vertical e contenção por linha sob altíssima volumetria. Descartado pela preferência
  por conditional writes sem lock e tema AWS.
