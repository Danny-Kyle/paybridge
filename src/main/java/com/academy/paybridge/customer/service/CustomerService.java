package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.NextOfKin;
import com.academy.paybridge.customer.domain.Relationship;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {
    Customer onboard(OnboardCommand cmd){};
    Customer getOrThrow(UUID id){};

    void verifyKyc(UUID id){};

    NextOfKin designateNextofKin(UUID customerId, UUID nokCustomerId, Relationship r){};

    Optional<NextOfKin> findNextofKin(UUID customerId){};

}
