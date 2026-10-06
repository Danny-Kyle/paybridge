package com.academy.paybridge.customer.domain;

import com.academy.paybridge.shared.audit.BaseEntity;

@Entity
public class Customer extends BaseEntity {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private KycStatus kycStatus;

    protected Customer() {}

    public void verifyKyc();

    public void rejectKyc();

    public static Customer onboard();
}
