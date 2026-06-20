# Premissas e pontos em aberto

Decisões tomadas na ausência de especificação explícita no enunciado. Registradas para
transparência na revisão (o desafio pede documentar motivadores e tradeoffs).

## Premissas

| # | Premissa | Motivação |
|---|---|---|
| P1 | **Body do POST**: `{ "type": "CREDIT\|DEBIT", "amount": { "value": <number>, "currency": "<ISO4217>" } }` | O enunciado define a *resposta* mas não o *request*; espelhamos o formato da resposta. |
| P2 | **Autenticação fora de escopo** | O enunciado não menciona auth; a abertura de conta também está fora de escopo. Em produção entraria API Gateway + authorizer. |
| P3 | **`created_at` da fila é string com epoch em segundos** (ex.: `"1634874339"`) | Confirmado decodificando o gerador Go fornecido. Parse: `String → long → Instant`. |
| P4 | **Conta não-ENABLED → transação recusada (FAILED)** | Decisão de negócio (ver [ADR-0006](adr/0006-account-status-gating.md)). O gerador só emite `ENABLED`, mas o campo existe. |
| P5 | **Moeda da transação deve casar com a da conta** | Evita misturar moedas no saldo. Mismatch → FAILED (`CURRENCY_MISMATCH`). |
| P6 | **Valor da transação > 0** | Valores zero/negativos rejeitados na validação de input. |
| P7 | **Fila Standard, entrega at-least-once, sem ordenação** | Confirmado no compose fornecido → idempotência obrigatória. |
| P8 | **Gerador é one-shot (rajada de 100k)** | Para simular criação contínua "24/7", re-executar o gerador ou publicar via AWS CLI. |
| P9 | **Conta é single-currency; moeda definida na criação (padrão BRL, configurável)** | A mensagem SQS não traz moeda, mas a resposta sempre tem `currency`. Transações em moeda diferente → FAILED (`CURRENCY_MISMATCH`, ver P5). |

## Pontos em aberto (a confirmar)

- Formato exato do body do POST (P1) — confirmar com quem propôs o desafio.
- Códigos HTTP para FAILED: usar `422 Unprocessable Entity` (negócio) vs `200` com `status: FAILED`.
  Proposta atual: **422** para recusa de negócio, `200` para sucesso.
- Política de paginação do extrato (tamanho de página default/máximo).
- Retenção/arquivamento de transações antigas (TTL no DynamoDB?).
