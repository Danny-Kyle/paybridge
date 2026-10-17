package com.academy.paybridge.settlement.web;
import com.academy.paybridge.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile({"local","test"})
@RequestMapping("/api/v1/local/settlements")
@PreAuthorize("hasRole('TREASURY')")
public class LocalTreasuryController {
 private final SettlementService service;
 public LocalTreasuryController(SettlementService service){this.service=service;}
 @PostMapping("/topups") public SettlementService.FloatBalance topUp(@Valid @RequestBody SettlementController.TopUp r){return service.topUp(r.reference(),r.amountMinor());}
}
