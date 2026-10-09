package com.academy.paybridge.customer.domain;

import com.academy.paybridge.shared.audit.BaseEntity;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {
    @Column(name = "first_name", nullable = false) private String firstName;
    @Column(name = "last_name", nullable = false) private String lastName;
    @Column(nullable = false) private String email;
    @Column(name = "phone_number", nullable = false) private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    private KycStatus kycStatus;

    protected Customer() {}

    public void verifyKyc(){
        if (kycStatus == KycStatus.REJECTED)
            throw new BusinessRuleException("KYC_ALREADY_REJECTED", "A Rejected customer cannot be verified");
        kycStatus = KycStatus.VERIFIED;
    };

    public void rejectKyc(){
        if (kycStatus == KycStatus.VERIFIED)
            throw new BusinessRuleException("KYC_ALREADY_VERIFIED", "A verified customer cannot be rejected");
        kycStatus = KycStatus.REJECTED;
    };

    public String fullName(){
        return firstName + "" + lastName;
    }
    public boolean isKycVerified(){
        return kycStatus == KycStatus.VERIFIED;
    }

    public String getFirstName (){
        return firstName;
    }

    public String getLastName (){
        return lastName;
    }

    public String getEmail (){
        return email;
    }

    public String getPhoneNumber (){
        return phoneNumber;
    }

    public KycStatus getKycStatus (){
        return kycStatus;
    }

    public static Customer onboard(String firstName, String lastName, String email, String phoneNumber) {
        var customer = new Customer();
        customer.firstName = firstName.trim();
        customer.lastName = lastName.trim();
        customer.email = email.trim().toLowerCase();
        customer.phoneNumber = phoneNumber.trim();
        customer.kycStatus = KycStatus.PENDING;

        return customer;
    };
}
