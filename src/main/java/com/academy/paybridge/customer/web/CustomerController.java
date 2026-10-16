package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.service.CustomerService;
import com.academy.paybridge.customer.service.OnboardCommand;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Onboarding, KYC and next of kin")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) { this.service = service; }

    @Operation(summary = "Onboard a customer",
            description = "Creates the customer with KYC status PENDING. A PayBridge NGN account is opened "
                    + "asynchronously by the account module, so it may take a moment to appear.")
    @ApiResponse(responseCode = "201", description = "Customer created")
    @PostMapping
    ResponseEntity<CustomerResponse> onboard(@Valid @RequestBody OnboardCustomerRequest req) {
        var customer = service.onboard(
                new OnboardCommand(req.firstName(), req.lastName(), req.email(), req.phoneNumber()));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.getId()))
                .body(CustomerResponse.from(customer));
    }

    @Operation(summary = "Get a customer")
    @GetMapping("/{customerId}")
    CustomerResponse get(@PathVariable UUID customerId) {
        return CustomerResponse.from(service.getOrThrow(customerId));
    }

    @Operation(summary = "Mark a customer's KYC as verified",
            description = "A REJECTED customer cannot be verified (422, KYC_ALREADY_REJECTED).")
    @PostMapping("/{customerId}/kyc/verify")
    CustomerResponse verifyKyc(@PathVariable UUID customerId) {
        return CustomerResponse.from(service.verifyKyc(customerId));
    }

    @Operation(summary = "Designate or change a customer's next of kin",
            description = "Upsert: one next of kin per customer. Used by the transfer routing rule.")
    @PutMapping("/{customerId}/next-of-kin")
    NextOfKinResponse designate(@PathVariable UUID customerId, @Valid @RequestBody DesignateNextOfKinRequest req) {
        return NextOfKinResponse.from(
                service.designateNextOfKin(customerId, req.nextOfKinCustomerId(), req.relationship()));
    }

    @Operation(summary = "Get a customer's next of kin")
    @GetMapping("/{customerId}/next-of-kin")
    NextOfKinResponse nextOfKin(@PathVariable UUID customerId) {
        return service.findNextOfKin(customerId)
                .map(NextOfKinResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("NextOfKin", customerId));
    }

    @Hidden                                   // dev-only check; keep it out of the docs
    @GetMapping("/hello")
    String hello() { return "virtual=" + Thread.currentThread().isVirtual(); }
}