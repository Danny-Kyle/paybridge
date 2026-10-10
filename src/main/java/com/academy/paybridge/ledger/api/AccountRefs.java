package com.academy.paybridge.ledger.api;

import java.util.UUID;

public final class AccountRefs {
    public static final String PAYSTACK_CLEARING = "SYSTEM:PAYSTACK_CLEARING";
    public static final String PAYSTACK_FLOAT = "SYSTEM:PAYSTACK_FLOAT";
    public static final String OPENING_EQUITY = "SYSTEM:OPENING_EQUITY";

    private AccountRefs() {}

    public static String customer(UUID accountId) { return "CUSTOMER:" + accountId; }
}