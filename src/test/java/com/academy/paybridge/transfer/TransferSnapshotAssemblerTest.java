package com.academy.paybridge.transfer;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountKind;
import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.NextOfKinSummary;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.domain.TransferSnapshot;
import com.academy.paybridge.transfer.service.TransferSnapshotAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransferSnapshotAssemblerTest {

    private final CustomerApi customerApi = mock(CustomerApi.class);
    private final AccountApi accountApi = mock(AccountApi.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
    private final TransferSnapshotAssembler assembler =
            new TransferSnapshotAssembler(customerApi, accountApi, executor, clock);

    @AfterEach void tearDown() { executor.close(); }

    private static AccountSummary account(UUID customerId) {
        return new AccountSummary(UUID.randomUUID(), customerId, "2000000001", "Name", "PBG", "Paybridge",
                AccountKind.INTERNAL, Currency.NGN);
    }

    @Test void assemblesRecipientAndNextOfKin() {
        UUID recipient = UUID.randomUUID(), nok = UUID.randomUUID();
        when(customerApi.exists(recipient)).thenReturn(true);
        when(customerApi.findNextOfKin(recipient)).thenReturn(Optional.of(new NextOfKinSummary(nok, "SIBLING")));
        when(accountApi.findPrimaryAccount(recipient)).thenReturn(Optional.of(account(recipient)));
        when(accountApi.findBalance(recipient)).thenReturn(Optional.of(Money.ngn(5_000)));
        when(accountApi.findPrimaryAccount(nok)).thenReturn(Optional.of(account(nok)));
        when(accountApi.findBalance(nok)).thenReturn(Optional.empty());

        TransferSnapshot s = assembler.assemble(recipient);

        assertThat(s.recipient().balance()).contains(Money.ngn(5_000));
        assertThat(s.nextOfKin()).isPresent();
        assertThat(s.nextOfKin().get().party().customerId()).isEqualTo(nok);
        assertThat(s.nextOfKin().get().party().balance()).isEmpty();
        assertThat(s.assembledAt()).isEqualTo(clock.instant());
    }

    @Test void noNextOfKinGivesEmpty() {
        UUID recipient = UUID.randomUUID();
        when(customerApi.exists(recipient)).thenReturn(true);
        when(customerApi.findNextOfKin(recipient)).thenReturn(Optional.empty());
        when(accountApi.findPrimaryAccount(recipient)).thenReturn(Optional.of(account(recipient)));
        when(accountApi.findBalance(recipient)).thenReturn(Optional.of(Money.ngn(1)));

        assertThat(assembler.assemble(recipient).nextOfKin()).isEmpty();
    }

    @Test void unknownCustomerFailsFast() {
        UUID recipient = UUID.randomUUID();
        when(customerApi.exists(recipient)).thenReturn(false);
        assertThatThrownBy(() -> assembler.assemble(recipient)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void recipientWithoutAccountIsRejected() {
        UUID recipient = UUID.randomUUID();
        when(customerApi.exists(recipient)).thenReturn(true);
        when(customerApi.findNextOfKin(recipient)).thenReturn(Optional.empty());
        when(accountApi.findPrimaryAccount(recipient)).thenReturn(Optional.empty());
        when(accountApi.findBalance(recipient)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> assembler.assemble(recipient)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void tasksRunOnVirtualThreads() {
        UUID recipient = UUID.randomUUID();
        AtomicBoolean virtual = new AtomicBoolean(false);
        when(customerApi.exists(recipient)).thenReturn(true);
        when(customerApi.findNextOfKin(recipient)).thenReturn(Optional.empty());
        when(accountApi.findPrimaryAccount(recipient)).thenReturn(Optional.of(account(recipient)));
        when(accountApi.findBalance(recipient)).thenAnswer(inv -> {
            virtual.set(Thread.currentThread().isVirtual());
            return Optional.of(Money.ngn(1));
        });
        assembler.assemble(recipient);
        assertThat(virtual).isTrue();
    }
}