package com.academy.paybridge.customer.domain;

import com.academy.paybridge.shared.audit.BaseEntity;

import java.util.UUID;

public class NextofKin extends BaseEntity {
    UUID customerId;
    UUID nextOfKinCustomerId;
    Relationship relationship;
}
