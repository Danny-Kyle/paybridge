package com.academy.paybridge.transfer.domain;

import java.time.Instant;
import java.util.Optional;

public record TransferSnapshot(PartySnapshot recipient, Optional<NextOfKinSnapshot> nextOfKin, Instant assembledAt) {}
