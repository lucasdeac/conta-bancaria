# conta-bancaria — Autorizador de Transações Financeiras

API de autorização de transações financeiras (crédito/débito) sobre contas bancárias,
com criação de contas consumida de uma fila **AWS SQS**. Projetada para alta volumetria,
com foco em **consistência, idempotência e resiliência**.

> **Status:** funcional e validado ponta a ponta (SQS → consumer → DynamoDB → API REST).
> Arquitetura documentada (9 ADRs), 30 testes verdes (unitários + integração), Docker e Postman prontos.

---

## Resumo executivo

**Problema:** autorizar transações de crédito/débito sobre contas bancárias, em alta volumetria,
sem nunca permitir saldo negativo e tolerando duplicatas (SQS *at-least-once* + retries de cliente).

**Os dois desafios centrais e como foram resolvidos:**

| Desafio | Solução | Por quê |
|---|---|---|
| **Consistência sob concorrência** (sem saldo negativo, sem *lost update*) | **Update condicional atômico** no DynamoDB: `UPDATE ... SET balance -= v WHERE balance >= v AND status=ENABLED AND currency=:c` | A guarda vive na escrita — sem lock, sem read-modify-write, sem race. Em falha, `ALL_OLD` classifica o motivo sem leitura extra. |
| **Idempotência** (duplicatas SQS + retry de POST) | `transactionId` como chave: *claim* `attribute_not_exists` → replay do resultado salvo | Reprocessar nunca dobra saldo; resposta determinística. |

**Decisões-chave (detalhe em [docs/adr/](docs/adr/)):**

| # | Decisão | Motivador |
|---|---|---|
| [001](docs/adr/0001-persistence-dynamodb.md) | DynamoDB | conditional writes nativos, escala, baixa latência |
| [002](docs/adr/0002-concurrency-conditional-update.md) | Claim + update condicional atômico | consistência sem lock (vs `TransactWriteItems`/optimistic/pessimistic) |
| [003](docs/adr/0003-idempotency-transaction-id.md) | Idempotência por `transactionId` | tolerar at-least-once e retries |
| [004](docs/adr/0004-money-minor-units.md) | `BigDecimal` + minor units | exatidão financeira (nunca `double`/`float`) |
| [005](docs/adr/0005-single-table-design.md) | Single-table + GSI1 | saldo O(1), extrato ordenado por tempo |
| [009](docs/adr/0009-hexagonal-architecture.md) | Arquitetura hexagonal | domínio testável, infra plugável |

**Resiliência:** Resilience4j (retry + backoff/full jitter, circuit breaker, time limiter), DLQ na fila,
health probes. **Segurança por padrão:** sem segredos hardcoded, IAM least privilege no deploy,
validação allow-list, queries parametrizadas (sem injeção), PII fora dos logs, container não-root.

**Qualidade:** 30 testes — incluindo concorrência (N débitos simultâneos → nunca saldo negativo) e
idempotência ponta a ponta — rodando com Testcontainers + LocalStack.

---

## Visão geral

- **Stack:** Java 21, Spring Boot 3.x, Gradle
- **Persistência:** DynamoDB (update condicional atômico, sem locks)
- **Mensageria:** AWS SQS (LocalStack em dev) — fila `conta-bancaria-criada`
- **Resiliência:** Resilience4j (retry + backoff/full jitter, circuit breaker, time limiter), DLQ
- **Observabilidade:** Micrometer/Prometheus, OpenTelemetry, Spring Actuator

## Fluxos

1. **Criação de conta (assíncrono):** um sistema externo publica `{"account": {...}}` na fila SQS.
   O consumer cria a conta com saldo **zero** (idempotente).
2. **Autorização (síncrono):** `POST /transactions/{transactionId}` aplica CREDIT (soma) ou
   DEBIT (subtrai). Débito que deixaria o saldo negativo é **recusado sem alterar o saldo**.

## Documentação

| Documento | Conteúdo |
|---|---|
| [docs/architecture.md](docs/architecture.md) | Arquitetura completa, diagramas, resiliência, deploy, pipeline |
| [docs/data-model.md](docs/data-model.md) | Modelagem DynamoDB (single-table) |
| [docs/assumptions.md](docs/assumptions.md) | Premissas e pontos em aberto |
| [docs/api/openapi.yaml](docs/api/openapi.yaml) | Contrato da API |
| [docs/adr/](docs/adr/) | Architecture Decision Records (decisões + tradeoffs) |

## Ambiente local

> **Pré-requisitos:** Docker + Docker Compose v2 e JDK 21. Clone o repositório e execute **todos os
> comandos a partir da raiz do projeto** — não há dependência de caminho absoluto nem de pastas externas.

Os dois composes ficam na **raiz do projeto** (tudo self-contained):

| Compose | Sobe |
|---|---|
| `docker-compose.yml` | LocalStack (SQS) + `message-generator` (gera 100k contas) |
| `docker-compose.app.yml` | DynamoDB Local + `init` (tabela/fila/DLQ) + `app` |

Como estão no mesmo diretório, sobem no **mesmo projeto Compose** (mesma rede). Basta combinar os
arquivos com `-f`.

### Subir o ambiente (recomendado)

```bash
# 1) Dependências em Docker — '--wait' garante que LocalStack e DynamoDB fiquem prontos
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --wait localstack dynamodb-local

# 2) init (cria fila/DLQ/tabela) + app
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d init app
```

A app sobe em `http://localhost:8080`, pronta para testar (veja [Obtendo IDs](#obtendo-ids-para-os-testes)).

### Gerar as 100k contas (opcional — teste de volumetria)

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml up message-generator
# aguarde "message-generator exited with code 0" — o consumer drena a fila automaticamente
```

### Rodar a app fora do container (dev, logs legíveis)

```bash
# sobe só as dependências + init...
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --wait localstack dynamodb-local
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d init
# ...e roda a app na máquina
./gradlew bootRun
```

### Derrubar tudo

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

> A imagem da app é multi-stage (build com JDK 21 → runtime só com JRE), usa **layered jars** e roda
> como **usuário não-root**. O `init` é **idempotente**: cria tabela/fila/DLQ e aplica a `RedrivePolicy`
> (DLQ) via `SetQueueAttributes` mesmo que a fila já tenha sido criada "pelada" pelo gerador.

> **Logs:** `./gradlew bootRun` usa o perfil `local` → logs em **texto legível**. O jar e a imagem
> Docker usam **JSON estruturado** (produção, com `requestId`). Loggers declarados com `@Slf4j` (Lombok).

### Endpoints locais

| Recurso | Endpoint | Credenciais |
|---|---|---|
| SQS (LocalStack) | `http://localhost:4566` | `test` / `test` |
| DynamoDB Local | `http://localhost:8000` | `test` / `test` (dummy) |
| App | `http://localhost:8080` | — |

> As credenciais `test/test` são o padrão do LocalStack/DynamoDB Local e **não são segredos reais**.
> Em produção, credenciais vêm de IAM roles — nunca hardcoded. Veja [ADR-0008](docs/adr/0008-deploy-and-pipeline.md).

## Testes

```bash
# Unitários (rápidos, sem Docker)
./gradlew test

# Integração (Testcontainers + LocalStack — exige Docker rodando)
./gradlew integrationTest
```

> **Docker via Colima?** O Testcontainers procura o socket em `/var/run/docker.sock`. Aponte para o Colima:
> ```bash
> export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
> export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
> ./gradlew integrationTest
> ```

### Cobertura atual

- **Unitários:** `Money` (escala/minor units), `RegisterAccountService`, `AuthorizeTransactionService`
  (aprovação, saldo insuficiente, conta desabilitada, moeda divergente, conta inexistente, replay, PENDING).
- **Integração (DynamoDB real):** criação idempotente, débito que recusa sem alterar saldo, moeda/status,
  **concorrência** (N débitos simultâneos nunca deixam o saldo negativo) e **idempotência** do POST duplicado.

## CI/CD

| Workflow | Tipo | O que faz |
|---|---|---|
| [`.github/workflows/ci.yml`](.github/workflows/ci.yml) | ✅ executável | Testes unitários + integração (Testcontainers) + build da imagem + **scan de vulnerabilidade (Trivy)** como gate. Roda em push na `main` e em PRs. |
| [`.github/workflows/cd.yml`](.github/workflows/cd.yml) | 📋 template | Estratégia de deploy **canary com rollback** (staging+smoke → canary 10% observando métricas → rollout 100%). Passos placeholder — substituir pelos comandos do alvo de cloud. |

A estratégia canary mitiga o risco de um bug atingir 100% dos clientes (requisito do desafio):
expõe a nova versão a uma fração do tráfego e só promove se as métricas (error rate, p99, taxa de
recusa, circuit breaker) seguirem saudáveis. Detalhe e tradeoffs em
[ADR-0008](docs/adr/0008-deploy-and-pipeline.md).

## Obtendo IDs para os testes

> **`transactionId`**: é escolhido pelo cliente (vai na URL). **Qualquer UUID serve** — a coleção
> Postman já gera um novo a cada envio (`{{$randomUUID}}`). Para gerar à mão: `uuidgen`.
>
> O que você precisa **obter** é um **`accountId` válido** (uma conta que já exista), para preencher
> a variável `accountId` no Postman.

Configure as credenciais locais (padrão LocalStack — não são segredos reais):

```bash
export AWS_DEFAULT_REGION=sa-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
```

Consulte o DynamoDB pelas contas que a aplicação **já persistiu** (são as que podem ser
autorizadas). Retorna até 5 `accountId`:

```bash
aws --endpoint-url=http://localhost:8000 dynamodb scan \
  --table-name ledger \
  --filter-expression "SK = :meta AND begins_with(PK, :acc)" \
  --expression-attribute-values '{":meta":{"S":"#META"},":acc":{"S":"ACC#"}}' \
  --max-items 5 \
  --query "Items[].accountId.S" --output text
```

> Esta é a fonte confiável para os testes: a autorização exige que a conta **exista no banco**,
> então usamos as contas que o consumer já processou da fila.

## Próximos passos

- [x] Scaffold do projeto (Gradle, Spring Boot, estrutura hexagonal)
- [x] Config + persistência DynamoDB (update condicional atômico)
- [x] Consumer SQS + idempotência
- [x] Autorização (claim → condicional → resolve) + API REST
- [x] Testes (unitários + integração com Testcontainers/LocalStack)
- [x] Coleção de requisições (Postman — em `postman/`)
- [x] Consulta de saldo + extrato (GET) — com paginação por cursor no GSI1
- [x] Dockerfile (multi-stage, não-root, layered jars) + serviço `app` no compose
- [x] Logs estruturados (JSON) com Logback + requestId (MDC)
- [x] Métricas de negócio (Micrometer/Prometheus): `authorizations.total`, `accounts.registered.total`
- [x] `HealthIndicator` customizado (readiness checa SQS + Dynamo)
- [x] CI (GitHub Actions): testes + build de imagem + scan de vulnerabilidade (Trivy)
- [x] CD canary com rollback — template documentado (`.github/workflows/cd.yml`)
- [ ] Tracing OpenTelemetry, reconciliação de `PENDING` — *propostos*
