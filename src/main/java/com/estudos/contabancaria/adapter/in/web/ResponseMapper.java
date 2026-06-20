package com.estudos.contabancaria.adapter.in.web;

import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse;
import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse.AccountView;
import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse.MoneyView;
import com.estudos.contabancaria.adapter.in.web.dto.AuthorizationResponse.TransactionView;
import com.estudos.contabancaria.adapter.in.web.dto.BalanceResponse;
import com.estudos.contabancaria.adapter.in.web.dto.StatementResponse;
import com.estudos.contabancaria.domain.model.Account;
import com.estudos.contabancaria.domain.model.Money;
import com.estudos.contabancaria.domain.model.Transaction;
import com.estudos.contabancaria.domain.port.in.AuthorizationResult;
import com.estudos.contabancaria.domain.port.out.StatementPage;

import java.time.ZoneId;
import java.util.List;

/** Converte resultados de domínio para os DTOs de resposta. */
final class ResponseMapper {

    private ResponseMapper() {
    }

    static AuthorizationResponse toResponse(AuthorizationResult result, ZoneId zone) {
        TransactionView txnView = txnView(result.transaction(), zone);
        MoneyView balance = result.accountBalance() == null ? null : money(result.accountBalance());
        AccountView accountView = new AccountView(result.accountId(), balance);
        return new AuthorizationResponse(txnView, accountView);
    }

    static BalanceResponse toBalance(Account account) {
        return new BalanceResponse(account.id(), money(account.balance()));
    }

    static StatementResponse toStatement(String accountId, StatementPage page, ZoneId zone) {
        List<TransactionView> items = page.items().stream()
                .map(t -> txnView(t, zone))
                .toList();
        return new StatementResponse(accountId, items, page.nextCursor());
    }

    private static TransactionView txnView(Transaction t, ZoneId zone) {
        return new TransactionView(
                t.id(),
                t.type(),
                money(t.amount()),
                t.status(),
                t.failureReason(),
                t.timestamp().atZone(zone).toOffsetDateTime().toString());
    }

    private static MoneyView money(Money money) {
        return new MoneyView(money.value(), money.currency().getCurrencyCode());
    }
}
