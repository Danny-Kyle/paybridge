package com.academy.paybridge.transfer.web;

import com.academy.paybridge.transfer.service.PaystackWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks/paystack")
class PaystackWebhookController {

    private final PaystackSignatureVerifier verifier;
    private final PaystackWebhookService service;

    PaystackWebhookController(PaystackSignatureVerifier verifier, PaystackWebhookService service) {
        this.verifier = verifier;
        this.service = service;
    }

    @PostMapping
    ResponseEntity<Void> receive(@RequestBody byte[] rawBody,
                                 @RequestHeader(value = "x-paystack-signature", required = false) String signature) {
        if (!verifier.isValid(rawBody, signature)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        service.handle(rawBody);
        return ResponseEntity.ok().build();
    }
}