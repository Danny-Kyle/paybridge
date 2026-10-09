package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.domain.NextOfKin;
import com.academy.paybridge.customer.domain.Relationship;

import java.util.UUID;

public record NextOfKinResponse(UUID customerId, UUID nextOfKinCustomerId, Relationship
relationship) {
    public static NextOfKinResponse from(NextOfKin n) {
        return new NextOfKinResponse(n.getNextOfKinCustomerId(), n.getCustomerId(), n.getRelationship());
    }
}
