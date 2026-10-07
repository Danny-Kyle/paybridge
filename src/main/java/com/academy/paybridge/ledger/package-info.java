@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"customer::api", "account::api", "transfer::api", "compliance::api", "shared"})
package com.academy.paybridge.ledger;