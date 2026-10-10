package com.academy.paybridge.transfer.client.paystack;

import com.academy.paybridge.transfer.client.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "paybridge.gateway.mode", havingValue = "paystack")
class PaystackTransferGateway implements TransferGateway {

    private final RestClient http;

    PaystackTransferGateway(RestClient.Builder builder, PaystackProperties props) {
        this.http = builder.baseUrl(props.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.secretKey())
                .build();
    }

    @Override
    public ResolvedAccount resolveAccount(String bankCode, String accountNumber) {
        var res = call(() -> http.get()
                .uri(u -> u.path("/bank/resolve")
                        .queryParam("account_number", accountNumber)
                        .queryParam("bank_code", bankCode).build())
                .retrieve()
                .body(new ParameterizedTypeReference<PaystackEnvelope<ResolveData>>() {}));
        return new ResolvedAccount(res.data().accountName(), accountNumber, bankCode);
    }

    @Override
    public String createRecipient(String name, String bankCode, String accountNumber) {
        var res = call(() -> http.post().uri("/transferrecipient")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "nuban", "name", name, "account_number", accountNumber,
                        "bank_code", bankCode, "currency", "NGN"))
                .retrieve()
                .body(new ParameterizedTypeReference<PaystackEnvelope<RecipientData>>() {}));
        return res.data().recipientCode();
    }

    @Override
    public GatewayTransferResult initiateTransfer(GatewayTransferRequest r) {
        var res = call(() -> http.post().uri("/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("source", "balance", "amount", r.amount().minorUnits(),
                        "recipient", r.recipientCode(), "reason", r.narration(), "reference", r.reference()))
                .retrieve()
                .body(new ParameterizedTypeReference<PaystackEnvelope<TransferData>>() {}));
        return new GatewayTransferResult(res.data().transferCode(), map(res.data().status()));
    }

    @Override
    public GatewayTransferResult verifyTransfer(String reference) {
        var res = call(() -> http.get().uri("/transfer/verify/{reference}", reference)
                .retrieve()
                .body(new ParameterizedTypeReference<PaystackEnvelope<TransferData>>() {}));
        return new GatewayTransferResult(res.data().transferCode(), map(res.data().status()));
    }

    private GatewayStatus map(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "success" -> GatewayStatus.SUCCESS;
            case "failed", "reversed" -> GatewayStatus.FAILED;
            case "otp" -> GatewayStatus.OTP_REQUIRED;
            default -> GatewayStatus.PENDING;
        };
    }

    private <T> PaystackEnvelope<T> call(Supplier<PaystackEnvelope<T>> request) {
        try {
            PaystackEnvelope<T> res = request.get();
            if (res == null || !res.status())
                throw new GatewayException("PAYSTACK_REJECTED", res == null ? "Empty response" : res.message());
            return res;
        } catch (RestClientResponseException e) {
            throw new GatewayException("PAYSTACK_" + e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new GatewayUnavailableException("Paystack unreachable: " + e.getMessage());
        }
    }
}