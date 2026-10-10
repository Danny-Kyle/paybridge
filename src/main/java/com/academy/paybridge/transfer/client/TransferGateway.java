package com.academy.paybridge.transfer.client;

public interface TransferGateway {
    ResolvedAccount resolveAccount(String bankCode, String accountNumber);                 // name enquiry
    String createRecipient(String accountName, String bankCode, String accountNumber);     // returns recipient_code
    GatewayTransferResult initiateTransfer(GatewayTransferRequest request);
    GatewayTransferResult verifyTransfer(String reference);                                // reconcile after a timeout
}