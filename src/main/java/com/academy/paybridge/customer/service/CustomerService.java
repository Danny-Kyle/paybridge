package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.api.CustomerOnboarded;
import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.NextOfKin;
import com.academy.paybridge.customer.domain.Relationship;
import com.academy.paybridge.customer.repository.CustomerRepository;
import com.academy.paybridge.customer.repository.NextOfKinRepository;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.academy.paybridge.shared.security.CurrentActor actor;
    private final NextOfKinRepository nextOfKin;
    private final ApplicationEventPublisher events;

    public CustomerService(CustomerRepository customers, NextOfKinRepository nextOfKin,
                           ApplicationEventPublisher events, org.springframework.jdbc.core.JdbcTemplate jdbc,
                           com.academy.paybridge.shared.security.CurrentActor actor) {
        this.jdbc=jdbc; this.actor=actor;
        this.customers = customers;
        this.nextOfKin = nextOfKin;
        this.events = events;
    }

    @Transactional
    public Customer onboard(OnboardCommand cmd) {
        if(!actor.hasRole("CUSTOMER")) throw new org.springframework.security.access.AccessDeniedException("Customer role required");
        if(Boolean.FALSE.equals(actor.jwt().getClaimAsBoolean("email_verified")))
            throw new org.springframework.security.access.AccessDeniedException("Verified identity email required");
        String email=actor.jwt().getClaimAsString("email");
        if(email==null || !Boolean.TRUE.equals(actor.jwt().getClaimAsBoolean("email_verified")) || !email.equalsIgnoreCase(cmd.email().trim()))
            throw new org.springframework.security.access.AccessDeniedException("Use the verified identity email");
        Integer linked=jdbc.queryForObject("select count(*) from customer_identity where issuer=? and subject=?",Integer.class,actor.issuer(),actor.subject());
        if(linked!=null && linked>0) throw new BusinessRuleException("CUSTOMER_ALREADY_LINKED","Identity already onboarded");
        if (customers.existsByEmailIgnoreCase(cmd.email()))
            throw new BusinessRuleException("EMAIL_TAKEN", "A customer with this email already exists.");
        Customer saved = customers.save(
                Customer.onboard(cmd.firstName(), cmd.lastName(), cmd.email(), cmd.phoneNumber()));
        customers.flush();
        jdbc.update("insert into customer_identity(issuer,subject,customer_id) values(?,?,?)",actor.issuer(),actor.subject(),saved.getId());
        events.publishEvent(new CustomerOnboarded(saved.getId(), saved.fullName(), saved.getEmail()));
        return saved;
    }

    public Customer getOrThrow(UUID id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional
    public Customer verifyKyc(UUID id) {
        Customer customer = getOrThrow(id);
        customer.verifyKyc();
        return customer;
    }

    @Transactional
    public NextOfKin designateNextOfKin(UUID customerId, UUID nokCustomerId, Relationship relationship) {
        getOrThrow(customerId);
        getOrThrow(nokCustomerId);
        return nextOfKin.findByCustomerId(customerId)
                .map(existing -> { existing.change(nokCustomerId, relationship); return existing; })
                .orElseGet(() -> nextOfKin.save(NextOfKin.designate(customerId, nokCustomerId, relationship)));
    }

    public Optional<NextOfKin> findNextOfKin(UUID customerId) {
        return nextOfKin.findByCustomerId(customerId);
    }
}