package com.academy.paybridge.customer;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.KycStatus;
import com.academy.paybridge.customer.domain.NextOfKin;
import com.academy.paybridge.customer.domain.Relationship;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerTest {

    private Customer customer() { return Customer.onboard(" Ada ", "Obi", " ADA@Example.com ", "08031230001"); }

    @Test void onboardNormalisesAndStartsPending() {
        Customer c = customer();
        assertThat(c.getEmail()).isEqualTo("ada@example.com");
        assertThat(c.getFirstName()).isEqualTo("Ada");
        assertThat(c.getKycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test void verifyThenRejectIsNotAllowed() {
        Customer c = customer();
        c.verifyKyc();
        assertThat(c.isKycVerified()).isTrue();
        assertThatThrownBy(c::rejectKyc).isInstanceOf(BusinessRuleException.class);
    }

    @Test void rejectedCustomerCannotBeVerified() {
        Customer c = customer();
        c.rejectKyc();
        assertThatThrownBy(c::verifyKyc).isInstanceOf(BusinessRuleException.class);
    }

    @Test void nextOfKinCannotBeSelf() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> NextOfKin.designate(id, id, Relationship.SPOUSE))
                .isInstanceOf(BusinessRuleException.class);
    }
}