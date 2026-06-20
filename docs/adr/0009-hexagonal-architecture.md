# ADR-0009 — Arquitetura hexagonal (ports & adapters)

**Status:** aceito · **Data:** 2026-06-14

## Contexto
O domínio (regras de saldo, idempotência, transição de estados) precisa ser testável e
independente de tecnologia (DynamoDB, SQS, Spring). Há duas formas de entrada (REST e SQS)
e uma de saída (persistência), candidatas naturais a adaptadores.

## Decisão
Adotar **arquitetura hexagonal**. Organização de pacotes:

```
domain/
  model/        entidades + value objects + enums (núcleo puro, sem framework)
  port/in/      portas de entrada (use cases) — próximos passos
  port/out/     portas de saída (AccountRepository, TransactionRepository)
application/    implementação dos use cases (orquestra domínio + portas) — próximos passos
adapter/
  in/web/       controllers REST + DTOs
  in/messaging/ listener SQS
  out/persistence/  adaptador DynamoDB (implementa as portas out)
config/         wiring de beans (AWS, etc.)
```

## Motivadores
- Núcleo de domínio testável sem AWS (mocka-se a porta).
- DynamoDB e SQS viram detalhes plugáveis (poderiam trocar sem tocar no domínio).
- Fronteiras explícitas entre regra de negócio e infraestrutura.

## Tradeoffs / consequências
- (+) Alta testabilidade e baixo acoplamento; evolução isolada.
- (−) Mais camadas/indireção e mapeamentos (domínio ↔ item DynamoDB) — custo aceitável.
- A lógica do **update condicional atômico** vive no adaptador de persistência (detalhe de
  tecnologia); o domínio só conhece a porta `AccountRepository` e o `BalanceMutationResult`.
