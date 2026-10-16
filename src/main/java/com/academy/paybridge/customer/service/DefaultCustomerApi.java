package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.CustomerSummary;
import com.academy.paybridge.customer.api.NextOfKinSummary;
import com.academy.paybridge.customer.repository.CustomerRepository;
import com.academy.paybridge.customer.repository.NextOfKinRepository;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DefaultCustomerApi implements CustomerApi {

    private final CustomerRepository customers;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.academy.paybridge.shared.security.CurrentActor actor;
    private final NextOfKinRepository nextOfKin;

    DefaultCustomerApi(CustomerRepository customers, NextOfKinRepository nextOfKin, org.springframework.jdbc.core.JdbcTemplate jdbc,
                       com.academy.paybridge.shared.security.CurrentActor actor) {
        this.jdbc=jdbc; this.actor=actor;
        this.customers = customers;
        this.nextOfKin = nextOfKin;
    }

    @Override
    public CustomerSummary getCustomer(UUID id) {
        return customers.findById(id)
                .map(c -> new CustomerSummary(c.getId(), c.fullName(), c.getEmail(), c.isKycVerified()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Override
    public UUID currentCustomerId() {
        var ids=jdbc.query("select customer_id from customer_identity where issuer=? and subject=?",
            (rs,n)->UUID.fromString(rs.getString(1)),actor.issuer(),actor.subject());
        if(ids.isEmpty()) throw new com.academy.paybridge.shared.exception.BusinessRuleException("CUSTOMER_NOT_LINKED","Onboard your customer profile first.");
        return ids.getFirst();
    }
    @Override
    public void requireOwner(UUID id) {
        if(!currentCustomerId().equals(id)) throw new org.springframework.security.access.AccessDeniedException("Customer ownership required");
    }
    @Override
    public boolean exists(UUID id) { return customers.existsById(id); }

    @Override
    public Optional<NextOfKinSummary> findNextOfKin(UUID customerId) {
        return nextOfKin.findByCustomerId(customerId)
                .map(n -> new NextOfKinSummary(n.getNextOfKinCustomerId(), n.getRelationship().name()));
    }
}