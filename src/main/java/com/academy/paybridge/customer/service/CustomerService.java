package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.NextofKin;
import com.academy.paybridge.customer.domain.Relationship;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class CustomerService {
    Customer onboard(OnboardCommand cmd){};
    Customer getOrThrow(UUID id){};

    void verifyKyc(UUID id){};

    NextofKin designateNextofKin(UUID customerId, UUID nokCustomerId, Relationship r){};

    Optional<NextofKin> findNextofKin(UUID customerId){};

}
