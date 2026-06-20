# ADR-0007 — Topologia de desenvolvimento local

**Status:** aceito · **Data:** 2026-06-14

## Contexto
O compose **fornecido** sobe LocalStack com `SERVICES=sqs` apenas (sem DynamoDB) e um gerador
*one-shot* de 100k contas. A fila é criada *on-the-fly* como Standard, **sem DLQ**.

## Decisão
- **Preservar** o compose fornecido sem alterações (`docker-compose.yml`, com LocalStack + gerador),
  mantido na **raiz do projeto** ao lado do nosso `docker-compose.app.yml` (tudo self-contained,
  para qualquer pessoa executar).
- `docker-compose.app.yml` traz **DynamoDB Local** + um serviço **`init`** + a **`app`**.
- O `init` (idempotente) cria a tabela `ledger`, a fila e a **DLQ com redrive policy**.
  Aplica a `RedrivePolicy` via `SetQueueAttributes` mesmo se a fila já existir (caso o gerador
  a tenha criado "pelada").
- Os dois arquivos sobem no **mesmo projeto Compose** (combinados com `-f`), compartilhando rede.
- A app acessa o SQS do LocalStack via `host.docker.internal:4566` (ou `localhost` quando
  rodando na máquina) e o DynamoDB Local via `localhost:8000`/`dynamodb-local:8000`.

## Motivadores
- Preserva o material original intacto.
- DynamoDB Local isola o comportamento do Dynamo do LocalStack.
- DLQ idempotente resolve o gap da fila criada sem redrive.

## Tradeoffs / consequências
- (+) Setup limpo e reproduzível; respeita o artefato fornecido.
- (−) Dois composes para orquestrar (documentado no README com a ordem de subida).
- (−) Conectividade entre composes via `host.docker.internal` (Docker Desktop) — alternativa:
  rodar a app na máquina apontando para `localhost`.

## Alternativas consideradas
- **Estender `SERVICES=sqs,dynamodb` no LocalStack fornecido:** mais simples (um endpoint),
  mas modifica o arquivo fornecido. Preterido.
- **App cria fila/DLQ no startup:** mistura infra com aplicação, menos fiel a produção (IaC).
