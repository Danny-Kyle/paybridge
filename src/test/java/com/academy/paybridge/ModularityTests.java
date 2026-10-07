package com.academy.paybridge;


import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

public class ModularityTests {
    private final ApplicationModules modules = ApplicationModules.of(PayBridgeApplication.class);

    @Test
    void verifiesModuleBoundaries(){
        modules.verify();
    }

    @Test
    void writeDocumentation(){
        new Documenter(modules).writeModulesAsPlantUml();
    }
}
