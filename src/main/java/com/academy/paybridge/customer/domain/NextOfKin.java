package com.academy.paybridge.customer.domain;

import com.academy.paybridge.shared.audit.BaseEntity;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "next_of_kin")
public class NextOfKin extends BaseEntity {
    @Column(name = "customer_id", nullable = false, updatable = false)  private UUID customerId;
    @Column(name = "next_of_kin_customerId", nullable = false) private UUID nextOfKinCustomerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Relationship relationship;

    protected NextOfKin(){};
    public static NextOfKin designate(UUID customerId, UUID nextOfKinCustomerId, Relationship relationship){
        if(customerId.equals(nextOfKinCustomerId))
            throw new BusinessRuleException("NOK_SELF", "You cannot choose yourself as a next of kin");
        var n = new NextOfKin();
        n.customerId = customerId;
        n.nextOfKinCustomerId = nextOfKinCustomerId;
        n.relationship = relationship;
        return n;
    }

    public void change(UUID nextOfKinCustomerId, Relationship relationship){
        if (customerId.equals(nextOfKinCustomerId))
            throw new BusinessRuleException("NOK_SELF", "You cannot choose yourself as a next of kin");
        this.nextOfKinCustomerId = nextOfKinCustomerId;
        this.relationship = relationship;
    }

    public UUID getCustomerId() {
        return customerId;
    }
    public UUID getNextOfKinCustomerId() {
        return nextOfKinCustomerId;
    }
    public Relationship getRelationship() {
        return relationship;
    }
}
