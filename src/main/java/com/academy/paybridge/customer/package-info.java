@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"transfer::api", "account::api", "ledger::api", "compliance::api", "shared"})
package com.academy.paybridge.customer;