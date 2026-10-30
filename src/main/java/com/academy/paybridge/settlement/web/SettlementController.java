package com.academy.paybridge.settlement.web;
import com.academy.paybridge.settlement.service.SettlementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@org.springframework.context.annotation.Profile({"local","test"})
@RestController
@RequestMapping("/api/v1/settlements")
@PreAuthorize("hasRole('TREASURY')")
public class SettlementController {
 private final SettlementService service;
 public SettlementController(SettlementService service){this.service=service;}
 public record TopUp(@NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9_-]+") String reference,@Positive long amountMinor){}
 public record Statement(@NotBlank @Size(max=100) String statementId,@PositiveOrZero long closingBalanceMinor){}
 @GetMapping("/float") public SettlementService.FloatBalance balance(){return service.balance();}
 @PostMapping("/reconciliations") public SettlementService.Reconciliation reconcile(@Valid @RequestBody Statement r){return service.reconcile(r.statementId(),r.closingBalanceMinor());}
}
