# ADR-0008 — Deploy em cloud e estratégia de pipeline

**Status:** proposto · **Data:** 2026-06-14

## Contexto
O desafio pede um diagrama de deploy em cloud pública e uma estratégia de pipeline que mitigue
o risco de um bug impactar todos os clientes.

## Decisão
- **Compute:** ECS **Fargate** (containers serverless, auto-scaling por CPU e por lag da fila).
- **Borda:** WAF → API Gateway → ALB → tasks Fargate.
- **Dados:** DynamoDB on-demand (auto-scaling); SQS + DLQ.
- **Segurança:** IAM **least privilege** por task role; **KMS** (at-rest) + TLS (in-transit);
  segredos em **Secrets Manager**; nada hardcoded.
- **Pipeline:** build → testes (unit + integração com Testcontainers/LocalStack) → análise
  estática → build de imagem + **scan de vulnerabilidade** → deploy staging + smoke →
  **canary 5–10%** com observação de métricas → rollout progressivo → **rollback automático**
  se métricas degradarem.

## Status de implementação
- **CI** ✅ *implementado* e executável: [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) —
  testes unitários, testes de integração (Testcontainers) e build + **scan de vulnerabilidade da
  imagem (Trivy)** como gate. Token do workflow com permissão mínima (`contents: read`).
- **CD** 📋 *proposto/template*: [`.github/workflows/cd.yml`](../../.github/workflows/cd.yml) — estrutura
  do canary com rollback, com passos placeholder (não há alvo de cloud real conectado).

## Motivadores
- Fargate evita gerenciar nós; escala bem para workloads com rajada.
- Canary limita o *blast radius* de um bug (mitiga o requisito explícito).
- Least privilege + KMS + Secrets Manager = postura de segurança de produção.

## Tradeoffs / consequências
- (+) Baixo overhead operacional; deploy seguro e reversível.
- (−) Canary exige métricas/health confiáveis (depende da observabilidade — ver architecture.md §6).
- (−) Cold start ocasional no Fargate; mitigável com min tasks.

## Alternativas consideradas
- **EKS (Kubernetes):** mais controle e portabilidade, maior complexidade operacional.
  Preterido para o escopo de estudo.
- **Blue/green:** também limita blast radius, mas troca 100% de uma vez; canary é mais granular.
