package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.service.CustomerService;
import com.academy.paybridge.customer.service.OnboardCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service){
        this.service = service;
    }

    @PostMapping()
    ResponseEntity<CustomerResponse> onboard(@Valid @RequestBody OnboardCustomerRequest request){
        var c = service.onboard(new OnboardCommand(request.firstName(), request.lastName(), request.email(), request.phoneNumber()));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + c.getId())).body(CustomerResponse.from(c));
    }

    @GetMapping("/{customerId}")
    CustomerResponse get(@PathVariable UUID customerId) { return CustomerResponse.from(service.getOrThrow(customerId)); }

    @PostMapping("/{customerId}/kyc/verify")
    CustomerResponse verifyKyc(@PathVariable UUID customerId) { return CustomerResponse.from(service.verifyKyc(customerId)); }

    @PutMapping("/{customerId}/next-of-kin")
    NextOfKinResponse designate(@PathVariable UUID customerId, @Valid @RequestBody DesignateNextofKinRequest req) {
        return CustomerResponse.from(service.designateNextofKin(customerId, req.customerId(), req.));
    }

    @GetMapping("/{customerId}/next-of-kin")
    NextOfKinResponse nextOfKin(@PathVariable UUID customerId) {
        // 404 via ResourceNotFoundException("NextOfKin", customerId) if none
    }

    @GetMapping("/hello")
    String hello() { return "virtual=" + Thread.currentThread().isVirtual(); }
}
