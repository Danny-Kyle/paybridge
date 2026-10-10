package com.academy.paybridge.transfer.domain;

import com.academy.paybridge.shared.money.Money;
import org.springframework.stereotype.Component;

@Component
public class NextOfKinRoutingPolicy implements RoutingPolicy {

    private final Money threshold;

    public NextOfKinRoutingPolicy(RoutingProperties props) {
        this.threshold = Money.ngn(props.nextOfKinThresholdMinor());
    }

    @Override
    public RoutingDecision decide(TransferSnapshot s) {
        PartySnapshot recipient = s.recipient();

        if (!overThreshold(recipient))
            return new RoutingDecision.Direct(recipient, "RECIPIENT_BELOW_THRESHOLD_OR_BALANCE_UNKNOWN");
        if (s.nextOfKin().isEmpty())
            return new RoutingDecision.Direct(recipient, "NO_NEXT_OF_KIN");

        var nok = s.nextOfKin().get();
        if (nok.party().account().isEmpty())
            return new RoutingDecision.Direct(recipient, "NEXT_OF_KIN_HAS_NO_ACCOUNT");
        if (overThreshold(nok.party()))
            return new RoutingDecision.Direct(recipient, "NEXT_OF_KIN_ALSO_OVER_THRESHOLD");   // no chaining

        return new RoutingDecision.RedirectToNextOfKin(nok.party(), recipient, nok.relationship(), "RECIPIENT_OVER_THRESHOLD");
    }

    private boolean overThreshold(PartySnapshot p) {
        return p.balance().map(b -> b.isGreaterThanOrEqualTo(threshold)).orElse(false);
    }
}