//package-info file to show which other modules may use this module
@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"customer::api", "account::api", "ledger::api", "compliance::api", "shared"})
package com.academy.paybridge.transfer;