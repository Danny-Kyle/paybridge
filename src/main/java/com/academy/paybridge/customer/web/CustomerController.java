package com.academy.paybridge.customer.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer")
public class CustomerController {

    @PostMapping()


    @GetMapping("/hello")
    public String hello(){
        return "Hello from " + Thread.currentThread().getName();
    }


    @PostMapping("/{customerId}/kyc/verify")
    ResponseEntity<>


    PUT    /{customerId}/next-of-kin
    GET    /{customerId}/next-of-kin

    @GetMapping("/{customerId}")
    public String customerId
}
