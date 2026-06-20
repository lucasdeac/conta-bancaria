#!/bin/sh
# Init idempotente da infra local:
#   - cria a tabela DynamoDB "ledger" (com GSI1) se não existir
#   - cria a fila principal e a DLQ, e aplica a RedrivePolicy (mesmo se a fila já existir)
#
# Idempotente: pode rodar várias vezes sem efeito colateral.
set -eu

: "${DYNAMO_ENDPOINT:?}"
: "${SQS_ENDPOINT:?}"
: "${TABLE_NAME:=ledger}"
: "${QUEUE_NAME:=conta-bancaria-criada}"
: "${DLQ_NAME:=conta-bancaria-criada-dlq}"

echo "==> DynamoDB: garantindo tabela '${TABLE_NAME}'"
if aws --endpoint-url "${DYNAMO_ENDPOINT}" dynamodb describe-table \
      --table-name "${TABLE_NAME}" >/dev/null 2>&1; then
  echo "    tabela já existe, ok"
else
  aws --endpoint-url "${DYNAMO_ENDPOINT}" dynamodb create-table \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions \
        AttributeName=PK,AttributeType=S \
        AttributeName=SK,AttributeType=S \
        AttributeName=GSI1PK,AttributeType=S \
        AttributeName=GSI1SK,AttributeType=S \
    --key-schema \
        AttributeName=PK,KeyType=HASH \
        AttributeName=SK,KeyType=RANGE \
    --global-secondary-indexes '[{
        "IndexName": "GSI1",
        "KeySchema": [
          {"AttributeName":"GSI1PK","KeyType":"HASH"},
          {"AttributeName":"GSI1SK","KeyType":"RANGE"}
        ],
        "Projection": {"ProjectionType":"ALL"}
      }]' \
    --billing-mode PAY_PER_REQUEST >/dev/null
  echo "    tabela criada"
fi

echo "==> SQS: garantindo DLQ '${DLQ_NAME}'"
DLQ_URL=$(aws --endpoint-url "${SQS_ENDPOINT}" sqs create-queue \
  --queue-name "${DLQ_NAME}" --query 'QueueUrl' --output text)
DLQ_ARN=$(aws --endpoint-url "${SQS_ENDPOINT}" sqs get-queue-attributes \
  --queue-url "${DLQ_URL}" --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)
echo "    DLQ ARN: ${DLQ_ARN}"

echo "==> SQS: garantindo fila principal '${QUEUE_NAME}'"
QUEUE_URL=$(aws --endpoint-url "${SQS_ENDPOINT}" sqs create-queue \
  --queue-name "${QUEUE_NAME}" --query 'QueueUrl' --output text)

echo "==> SQS: aplicando RedrivePolicy + VisibilityTimeout (idempotente)"
REDRIVE="{\"deadLetterTargetArn\":\"${DLQ_ARN}\",\"maxReceiveCount\":\"5\"}"
aws --endpoint-url "${SQS_ENDPOINT}" sqs set-queue-attributes \
  --queue-url "${QUEUE_URL}" \
  --attributes "{\"RedrivePolicy\":\"$(echo "${REDRIVE}" | sed 's/"/\\"/g')\",\"VisibilityTimeout\":\"30\"}"

echo "==> Init concluído."
echo "    Fila:  ${QUEUE_URL}"
echo "    DLQ:   ${DLQ_URL}"
echo "    Tabela: ${TABLE_NAME}"
