package com.academy.paybridge.remittance.web;
import java.util.UUID;
import com.academy.paybridge.remittance.api.RemittanceView;
import com.academy.paybridge.remittance.service.RemittanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@org.springframework.context.annotation.Profile({"local","test"})
@RestController
@RequestMapping("/api/v1/remittances")
@PreAuthorize("hasRole('CUSTOMER')")
public class RemittanceController {
 private final RemittanceService service;
 public RemittanceController(RemittanceService service){this.service=service;}
 public record Create(@Positive long sourceUsdMinor,@NotBlank @Size(max=100) String beneficiaryName,
  @NotBlank @Pattern(regexp="[0-9]{3,6}") String bankCode,@NotBlank @Pattern(regexp="[0-9]{10}") String accountNumber){}
 public record Convert(@NotNull UUID quoteId){}
 @PostMapping public RemittanceView create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Create r){
  if(key.isBlank() || key.length()>64)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Idempotency-Key must contain 1-64 characters");
  return service.create(key,r.sourceUsdMinor(),r.beneficiaryName(),r.bankCode(),r.accountNumber());
 }
 @GetMapping("/{id}") public RemittanceView get(@PathVariable UUID id){return service.get(id);}
 @PostMapping("/{id}/convert") public RemittanceView convert(@PathVariable UUID id,@Valid @RequestBody Convert r){return service.convertAndQueue(id,r.quoteId());}
 @PostMapping("/{id}/retry") public RemittanceView retry(@PathVariable UUID id){return service.retry(id);}
}
