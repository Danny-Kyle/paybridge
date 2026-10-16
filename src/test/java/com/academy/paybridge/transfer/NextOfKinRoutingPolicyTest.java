package com.academy.paybridge.transfer;

import com.academy.paybridge.account.api.AccountKind;
import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.shared.money.Currency;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NextOfKinRoutingPolicyTest {

    private static final long THRESHOLD = 1_000_000_000L;                      // ₦10,000,000
    private final NextOfKinRoutingPolicy policy = new NextOfKinRoutingPolicy(new RoutingProperties(THRESHOLD));

    private static PartySnapshot party(Optional<Money> balance, boolean hasAccount) {
        UUID id = UUID.randomUUID();
        Optional<AccountSummary> account = hasAccount
                ? Optional.of(new AccountSummary(UUID.randomUUID(), id, "2000000001", "Name", "PBG", "Paybridge",
                AccountKind.INTERNAL, Currency.NGN))
                : Optional.empty();
        return new PartySnapshot(id, account, balance);
    }

    private static TransferSnapshot snapshot(PartySnapshot recipient, PartySnapshot nok) {
        return new TransferSnapshot(recipient,
                nok == null ? Optional.empty() : Optional.of(new NextOfKinSnapshot("SIBLING", nok)), Instant.now());
    }

    @Test void belowThresholdGoesDirect() {
        var d = policy.decide(snapshot(party(Optional.of(Money.ngn(THRESHOLD - 1)), true), party(Optional.empty(), true)));
        assertThat(d).isInstanceOf(RoutingDecision.Direct.class);
        assertThat(d.reason()).isEqualTo("RECIPIENT_BELOW_THRESHOLD_OR_BALANCE_UNKNOWN");
    }

    @Test void exactlyAtThresholdRedirects() {
        var d = policy.decide(snapshot(party(Optional.of(Money.ngn(THRESHOLD)), true), party(Optional.empty(), true)));
        assertThat(d).isInstanceOf(RoutingDecision.RedirectToNextOfKin.class);
    }

    @Test void unknownBalanceGoesDirect() {
        var d = policy.decide(snapshot(party(Optional.empty(), true), party(Optional.empty(), true)));
        assertThat(d).isInstanceOf(RoutingDecision.Direct.class);
    }

    @Test void noNextOfKinGoesDirect() {
        var d = policy.decide(snapshot(party(Optional.of(Money.ngn(THRESHOLD * 2)), true), null));
        assertThat(d.reason()).isEqualTo("NO_NEXT_OF_KIN");
    }

    @Test void nextOfKinWithoutAccountGoesDirect() {
        var d = policy.decide(snapshot(party(Optional.of(Money.ngn(THRESHOLD * 2)), true), party(Optional.empty(), false)));
        assertThat(d.reason()).isEqualTo("NEXT_OF_KIN_HAS_NO_ACCOUNT");
    }

    @Test void richNextOfKinDoesNotChain() {
        var d = policy.decide(snapshot(party(Optional.of(Money.ngn(THRESHOLD * 2)), true),
                party(Optional.of(Money.ngn(THRESHOLD * 3)), true)));
        assertThat(d.reason()).isEqualTo("NEXT_OF_KIN_ALSO_OVER_THRESHOLD");
    }

    @Test void redirectsToNextOfKinWhenRecipientIsRich() {
        PartySnapshot nok = party(Optional.empty(), true);
        var d = policy.decide(snapshot(party(Optional.of(Money.ngn(THRESHOLD * 5)), true), nok));
        assertThat(d).isInstanceOf(RoutingDecision.RedirectToNextOfKin.class);
        assertThat(d.destination()).isEqualTo(nok);
    }
}