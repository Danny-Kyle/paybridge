//package com.academy.paybridge.customer.service;
//
//import com.academy.paybridge.customer.domain.Customer;
//import com.academy.paybridge.customer.domain.NextOfKin;
//import com.academy.paybridge.customer.domain.Relationship;
//import com.academy.paybridge.customer.repository.CustomerRepository;
//import com.academy.paybridge.customer.repository.NextOfKinRepository;
//import com.academy.paybridge.shared.exception.ResourceNotFoundException;
//import org.springframework.context.ApplicationEventPublisher;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Optional;
//import java.util.UUID;
//
//@Service
//@Transactional(readOnly = false)
//public class CustomerService {
//    private final CustomerRepository customers;
//    private final NextOfKinRepository nextKins;
//    private final ApplicationEventPublisher publisher;
//
//    public CustomerService(CustomerRepository customers, NextOfKinRepository nextKins,  ApplicationEventPublisher publisher) {
//        this.customers = customers;
//        this.nextKins = nextKins;
//        this.publisher = publisher;
//    }
//
//    public Customer onboard(OnboardCommand cmd){
////        if(cmd.email().)
//        var newCustomer = new OnboardCommand();
//    }
//
//    public Customer getOrThrow(UUID id){
//        if (id == null)
//            throw new ResourceNotFoundException("Customer", id);
//        customers.findById(id);
//    };
//
//    @Transactional
//    void verifyKyc(UUID id){
//        //
//    };
//
//    public NextOfKin designateNextofKin(UUID customerId, UUID nokCustomerId, Relationship r){
//        if (customerId == null || nokCustomerId == null)
//            getOrThrow(customerId);
//    };
//
//    public Optional<NextOfKin> findNextOfKin(UUID customerId){
//        return nextKins.findByCustomerId(customerId);
//    };
//
//}
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
    private final NextOfKinRepository nextOfKin;
    private final ApplicationEventPublisher events;

    public CustomerService(CustomerRepository customers, NextOfKinRepository nextOfKin,
                           ApplicationEventPublisher events) {
        this.customers = customers;
        this.nextOfKin = nextOfKin;
        this.events = events;
    }

    @Transactional
    public Customer onboard(OnboardCommand cmd) {
        if (customers.existsByEmailIgnoreCase(cmd.email()))
            throw new BusinessRuleException("EMAIL_TAKEN", "A customer with this email already exists.");
        Customer saved = customers.save(
                Customer.onboard(cmd.firstName(), cmd.lastName(), cmd.email(), cmd.phoneNumber()));
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