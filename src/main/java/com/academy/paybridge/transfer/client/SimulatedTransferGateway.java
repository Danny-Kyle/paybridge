package com.academy.paybridge.transfer.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "paybridge.gateway.mode", havingValue = "simulated", matchIfMissing = true)
class SimulatedTransferGateway implements TransferGateway {

    @Override
    public ResolvedAccount resolveAccount(String bankCode, String accountNumber) {
        return new ResolvedAccount("Simulated Account " + accountNumber, accountNumber, bankCode);
    }

    @Override
    public String createRecipient(String accountName, String bankCode, String accountNumber) {
        return "RCP_sim_" + bankCode + "_" + accountNumber;
    }

    @Override
    public GatewayTransferResult initiateTransfer(GatewayTransferRequest r) {
        // demo hook: an amount ending in 13 kobo simulates a gateway rejection (exercises the refund path)
        GatewayStatus status = r.amount().minorUnits() % 100 == 13 ? GatewayStatus.FAILED : GatewayStatus.SUCCESS;
        return new GatewayTransferResult("TRF_sim_" + UUID.randomUUID().toString().substring(0, 8), status);
    }

    @Override
    public GatewayTransferResult verifyTransfer(String reference) {
        return new GatewayTransferResult("TRF_sim_" + reference, GatewayStatus.SUCCESS);
    }
}