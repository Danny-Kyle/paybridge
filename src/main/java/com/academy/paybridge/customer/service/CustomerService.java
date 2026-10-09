package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.NextOfKin;
import com.academy.paybridge.customer.domain.Relationship;
import com.academy.paybridge.customer.repository.CustomerRepository;
import com.academy.paybridge.customer.repository.NextOfKinRepository;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = false)
public class CustomerService {
    private final CustomerRepository customers;
    private final NextOfKinRepository nextKins;
    private final ApplicationEventPublisher publisher;

    public CustomerService(CustomerRepository customers, NextOfKinRepository nextKins,  ApplicationEventPublisher publisher) {
        this.customers = customers;
        this.nextKins = nextKins;
        this.publisher = publisher;
    }

    public Customer onboard(OnboardCommand cmd){
//        if(cmd.email().)
        var newCustomer = new OnboardCommand();
    }

    public Customer getOrThrow(UUID id){
        if (id == null)
            throw new ResourceNotFoundException("Customer", id);
        customers.findById(id);
    };

    @Transactional
    void verifyKyc(UUID id){
        //
    };

    public NextOfKin designateNextofKin(UUID customerId, UUID nokCustomerId, Relationship r){
        if (customerId == null || nokCustomerId == null)
            getOrThrow(customerId);
    };

    public Optional<NextOfKin> findNextOfKin(UUID customerId){
        return nextKins.findByCustomerId(customerId);
    };

}
