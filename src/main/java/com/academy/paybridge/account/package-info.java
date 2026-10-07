@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"customer::api", "transfer::api", "ledger::api", "compliance::api", "shared"})
package com.academy.paybridge.account;