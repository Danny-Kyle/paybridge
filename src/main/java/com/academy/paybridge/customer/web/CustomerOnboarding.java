package com.academy.paybridge.customer.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerOnboarding {

    @GetMapping("/hello")
    public String hello(){
        return "Hello from " + Thread.currentThread().getName();
    }
}
