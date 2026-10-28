package com.academy.paybridge.remittance.web;
import java.util.UUID;
import com.academy.paybridge.remittance.api.RemittanceView;
import com.academy.paybridge.remittance.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
@Profile({"local","test"})
@RequestMapping("/api/v1/local/remittances")
@PreAuthorize("hasRole('TREASURY')")
public class LocalRemittanceController {
 private final RemittanceService service;private final LocalPayoutWorker worker;private final LocalPayoutAdapter adapter;
 public LocalRemittanceController(RemittanceService service,LocalPayoutWorker worker,LocalPayoutAdapter adapter){this.service=service;this.worker=worker;this.adapter=adapter;}
 public record Funding(@NotBlank @Size(max=80) String fundingReference,@Positive long sourceUsdMinor){}
 public record Outcome(@NotBlank @Pattern(regexp="SUCCESS|FAILED|UNKNOWN") String outcome){}
 @PostMapping("/{id}/funding") public RemittanceView fund(@PathVariable UUID id,@Valid @RequestBody Funding r){return service.confirmLocalFunding(id,r.fundingReference(),r.sourceUsdMinor());}
 @PostMapping("/{id}/process") public RemittanceView process(@PathVariable UUID id){return worker.process(id);}
 @PostMapping("/{id}/outcome") public void outcome(@PathVariable UUID id,@Valid @RequestBody Outcome r){adapter.setOutcome(service.inspect(id).payoutReference(),r.outcome());}
 @PostMapping("/recover") public void recover(){worker.recover();}
}
