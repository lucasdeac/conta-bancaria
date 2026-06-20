# ADR-0004 — Dinheiro em minor units + BigDecimal

**Status:** aceito · **Data:** 2026-06-14

## Contexto
Operações financeiras não toleram erro de arredondamento. Ponto flutuante binário
(`double`/`float`) não representa valores decimais exatamente.

## Decisão
- Domínio usa **`BigDecimal`** com escala definida pela moeda (ISO 4217).
- Persistência em DynamoDB como **inteiro em *minor units*** (centavos) no tipo `Number`.
- Conversão centralizada num Value Object `Money(value, currency)`.

## Motivadores
- Exatidão aritmética (sem erro de arredondamento binário).
- `Number` do DynamoDB é decimal de precisão arbitrária → mapeia bem para `BigDecimal`.
- Inteiro em centavos simplifica comparações atômicas (`balanceMinor >= :v`).

## Tradeoffs / consequências
- (+) Correção financeira garantida.
- (−) Conversão valor↔minor units exige cuidado com a escala por moeda (ex.: JPY tem 0 casas).
  Centralizada no `Money` para evitar dispersão.

## Proibições (regra de segurança)
- **Nunca** usar `double`/`float` para valores monetários no domínio ou na API.
