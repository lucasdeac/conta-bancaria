# ADR-0005 — Single-table design + GSI

**Status:** aceito · **Data:** 2026-06-14

## Contexto
DynamoDB favorece modelagem orientada a padrões de acesso. Precisamos: consultar saldo,
extrato por conta ordenado por tempo, criar conta e processar transação idempotente.

## Decisão
Tabela única `ledger` com itens **Account** (`PK=ACC#<id>`, `SK=#META`) e **Transaction**
(`PK=TXN#<id>`, `SK=#META`), e um **GSI1** (`GSI1PK=ACC#<id>`, `GSI1SK=<timestamp>#TXN#<id>`)
para o extrato. Detalhe em [data-model.md](../data-model.md).

## Motivadores
- Saldo e dedup de transação → `GetItem` O(1).
- Extrato → `Query` no GSI1, ordenado por tempo, paginado.
- Uma tabela reduz custo operacional.

## Tradeoffs / consequências
- (+) Acessos eficientes e previsíveis; menos recursos para operar.
- (−) Modelagem menos intuitiva que tabelas relacionais separadas.
- (−) GSI replica dados (custo de escrita/armazenamento) — aceitável pelo ganho no extrato.

## Alternativas consideradas
- **Tabelas separadas (accounts, transactions):** mais intuitivo, mas perde a coesão do
  single-table e exige mais recursos. Razoável; preterido por idiomático.
