@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"customer::api", "account::api", "ledger::api", "compliance::api", "shared"})
package com.academy.paybridge.transfer;