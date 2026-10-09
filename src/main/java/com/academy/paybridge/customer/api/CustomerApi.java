package com.academy.paybridge.customer.api;

import java.util.Optional;
import java.util.UUID;

public interface CustomerApi {

    CustomerSummary getCustomer(UUID id);
    boolean exists(UUID id);
    Optional<NextOfKinSummary> findNextOfKin(UUID customerId);
}
