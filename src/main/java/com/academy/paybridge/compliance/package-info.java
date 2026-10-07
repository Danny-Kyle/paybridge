@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"customer::api", "account::api", "ledger::api", "transfer::api", "shared"})
package com.academy.paybridge.compliance;