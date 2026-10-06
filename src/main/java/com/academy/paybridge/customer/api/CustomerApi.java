package com.academy.paybridge.customer.api;

import com.academy.paybridge.customer.domain.NextofKin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
public interface CustomerApi {

    CustomerSummary getCustomer(UUID id);
    boolean exists(UUID id);
    Optional<NextofKinSummary> getNextKin(UUID customerId);

    @GetMapping
    public String getCustomer(@PathVariable String customerId) {};
}
