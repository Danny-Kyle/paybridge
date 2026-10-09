package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.service.CustomerService;
import com.academy.paybridge.customer.service.OnboardCommand;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) { this.service = service; }

    @PostMapping
    ResponseEntity<CustomerResponse> onboard(@Valid @RequestBody OnboardCustomerRequest req) {
        var customer = service.onboard(
                new OnboardCommand(req.firstName(), req.lastName(), req.email(), req.phoneNumber()));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.getId()))
                .body(CustomerResponse.from(customer));
    }

    @GetMapping("/{customerId}")
    CustomerResponse get(@PathVariable UUID customerId) {
        return CustomerResponse.from(service.getOrThrow(customerId));
    }

    @PostMapping("/{customerId}/kyc/verify")
    CustomerResponse verifyKyc(@PathVariable UUID customerId) {
        return CustomerResponse.from(service.verifyKyc(customerId));
    }

    @PutMapping("/{customerId}/next-of-kin")
    NextOfKinResponse designate(@PathVariable UUID customerId, @Valid @RequestBody DesignateNextOfKinRequest req) {
        return NextOfKinResponse.from(
                service.designateNextOfKin(customerId, req.nextOfKinCustomerId(), req.relationship()));
    }

    @GetMapping("/{customerId}/next-of-kin")
    NextOfKinResponse nextOfKin(@PathVariable UUID customerId) {
        return service.findNextOfKin(customerId)
                .map(NextOfKinResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("NextOfKin", customerId));
    }

    @GetMapping("/hello")
    String hello() { return "virtual=" + Thread.currentThread().isVirtual(); }   // delete later
}