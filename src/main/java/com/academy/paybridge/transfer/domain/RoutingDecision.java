package com.academy.paybridge.transfer.domain;

public sealed interface RoutingDecision permits RoutingDecision.Direct, RoutingDecision.RedirectToNextOfKin {
    PartySnapshot destination();
    String reason();

    record Direct(PartySnapshot destination, String reason) implements RoutingDecision {}

    record RedirectToNextOfKin(PartySnapshot destination, PartySnapshot originalRecipient,
                               String relationship, String reason) implements RoutingDecision {}
}