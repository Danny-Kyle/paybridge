package com.academy.paybridge.transfer.service;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import com.academy.paybridge.transfer.domain.NextOfKinSnapshot;
import com.academy.paybridge.transfer.domain.PartySnapshot;
import com.academy.paybridge.transfer.domain.TransferSnapshot;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Clock.*;
import java.time.Duration;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class TransferSnapshotAssembler {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final CustomerApi customerApi;
    private final AccountApi accountApi;
    private final Executor executor;
    private final Clock clock;

    public TransferSnapshotAssembler(CustomerApi customerApi, AccountApi accountApi,
                                     @Qualifier("applicationTaskExecutor") Executor executor, Clock clock) {
        this.customerApi = customerApi; this.accountApi = accountApi;
        this.executor = executor; this.clock = clock;
    }

    public TransferSnapshot assemble(UUID recipientId) {
        if (!customerApi.exists(recipientId)) throw new ResourceNotFoundException("Customer", recipientId);   // fail fast

        // Chain A: Tasks 1 and 2
        var accountF = async(() -> accountApi.findPrimaryAccount(recipientId));
        var balanceF = async(() -> accountApi.findBalance(recipientId));

        // Chain B: Task 3, then fan out Tasks 4 and 5
        CompletableFuture<Optional<NextOfKinSnapshot>> nokF =
                async(() -> customerApi.findNextOfKin(recipientId))
                        .thenCompose(maybeNok -> maybeNok
                                .map(nok -> {
                                    UUID nokId = nok.nextOfKinCustomerId();
                                    var nokAccountF = async(() -> accountApi.findPrimaryAccount(nokId));   // Task 4
                                    var nokBalanceF = async(() -> accountApi.findBalance(nokId));          // Task 5
                                    return nokAccountF.thenCombine(nokBalanceF, (acc, bal) ->
                                            Optional.of(new NextOfKinSnapshot(nok.relationship(), new PartySnapshot(nokId, acc, bal))));
                                })
                                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));

        try {
            CompletableFuture.allOf(accountF, balanceF, nokF)
                    .orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;      // keep our business exceptions intact
            throw e;
        }

        var account = accountF.join();
        if (account.isEmpty()) throw new BusinessRuleException("RECIPIENT_HAS_NO_ACCOUNT", "Recipient has no account.");

        return new TransferSnapshot(new PartySnapshot(recipientId, account, balanceF.join()), nokF.join(), clock.instant());
    }

    private <T> CompletableFuture<T> async(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }
}
