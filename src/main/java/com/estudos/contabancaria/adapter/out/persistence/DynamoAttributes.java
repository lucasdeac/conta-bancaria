package com.estudos.contabancaria.adapter.out.persistence;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;

/** Helpers para construir e ler {@link AttributeValue} do DynamoDB de forma concisa. */
final class DynamoAttributes {

    private DynamoAttributes() {
    }

    /** Constrói um AttributeValue do tipo String (S). */
    static AttributeValue stringValue(String value) {
        return AttributeValue.builder().s(value).build();
    }

    /** Constrói um AttributeValue do tipo Number (N). */
    static AttributeValue numberValue(long value) {
        return AttributeValue.builder().n(Long.toString(value)).build();
    }

    /** Lê o valor String de um atributo do item ({@code null} se ausente). */
    static String readString(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        return value == null ? null : value.s();
    }

    /** Lê o valor numérico (long) de um atributo do item. */
    static long readLong(Map<String, AttributeValue> item, String key) {
        return Long.parseLong(item.get(key).n());
    }
}
