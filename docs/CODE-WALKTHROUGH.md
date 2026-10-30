# Code walkthrough

Complete files for the changed features. Follow IMPLEMENTATION-GUIDE.md for reasoning, run order and Swagger examples. This reference presents settlement, remittance and FX in your requested order; security/configuration follows. The entire application is also in src/.

## 1. Settlement

### src/main/java/com/academy/paybridge/settlement/api/SettlementApi.java

```java
package com.academy.paybridge.settlement.api;
import java.util.UUID;
public interface SettlementApi {
 void reserve(UUID operationId,long ngnMinor);
 void capture(UUID operationId);
 void release(UUID operationId);
}
```

### src/main/java/com/academy/paybridge/settlement/api/package-info.java

```java
@org.springframework.modulith.NamedInterface("api")
package com.academy.paybridge.settlement.api;
```

### src/main/java/com/academy/paybridge/settlement/package-info.java

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "ledger::api"})
package com.academy.paybridge.settlement;
```

### src/main/java/com/academy/paybridge/settlement/service/SettlementService.java

```java
package com.academy.paybridge.settlement.service;
import java.time.*;
import java.util.*;
import com.academy.paybridge.settlement.api.SettlementApi;
import com.academy.paybridge.ledger.api.*;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@org.springframework.context.annotation.Profile({"local","test"})
@Service
@Transactional
public class SettlementService implements SettlementApi {
 private final JdbcTemplate db; private final LedgerApi ledger; private final Clock clock;
 public SettlementService(JdbcTemplate db,LedgerApi ledger,Clock clock){this.db=db;this.ledger=ledger;this.clock=clock;}
 public record FloatBalance(long cashMinor,long reservedMinor,long availableMinor){}
 public record Reconciliation(UUID id,long reportedMinor,long bookMinor,long differenceMinor,String status){}
 public FloatBalance balance(){return db.queryForObject("select cash_minor,reserved_minor from settlement_float where id=1",(r,n)->new FloatBalance(r.getLong(1),r.getLong(2),r.getLong(1)-r.getLong(2)));}
 private FloatBalance lock(){return db.queryForObject("select cash_minor,reserved_minor from settlement_float where id=1 for update",(r,n)->new FloatBalance(r.getLong(1),r.getLong(2),r.getLong(1)-r.getLong(2)));}
 public FloatBalance topUp(String reference,long amount){
  lock();
  var old=db.query("select amount_minor from settlement_topup where reference=?",(r,n)->r.getLong(1),reference);
  if(!old.isEmpty()){if(old.getFirst()!=amount)throw conflict();return balance();}
  if(amount<=0)throw new BusinessRuleException("INVALID_AMOUNT","Positive top-up required");
  long next=Math.addExact(balance().cashMinor(),amount);
  db.update("insert into settlement_topup(reference,amount_minor,created_at) values(?,?,?)",reference,amount,java.sql.Timestamp.from(clock.instant()));
  db.update("update settlement_float set cash_minor=? where id=1",next);
  ledger.post(new PostJournalCommand("topup:"+reference,JournalType.FLOAT_TOPUP,"Simulated treasury float funding",List.of(
   EntryLine.debit("SYSTEM:PROVIDER_NGN_CASH",Money.ngn(amount)),EntryLine.credit("SYSTEM:TREASURY_FUNDING",Money.ngn(amount)))));
  return balance();
 }
 public void reserve(UUID op,long amount){
  FloatBalance b=lock();
  var old=db.queryForList("select amount_minor,status from settlement_reservation where operation_id=?",op);
  if(!old.isEmpty()){
   if(((Number)old.getFirst().get("amount_minor")).longValue()!=amount || !"RESERVED".equals(old.getFirst().get("status")))throw conflict();
   return;
  }
  if(amount<=0 || b.availableMinor()<amount)throw new BusinessRuleException("INSUFFICIENT_FLOAT","NGN float is insufficient");
  db.update("insert into settlement_reservation(operation_id,amount_minor,status) values(?,?,'RESERVED')",op,amount);
  db.update("update settlement_float set reserved_minor=? where id=1",Math.addExact(b.reservedMinor(),amount));
 }
 public void capture(UUID op){finish(op,true);}
 public void release(UUID op){finish(op,false);}
 private void finish(UUID op,boolean capture){
  var b=lock();
  var rows=db.queryForList("select amount_minor,status from settlement_reservation where operation_id=?",op);
  if(rows.isEmpty())throw new BusinessRuleException("NO_RESERVATION","Float reservation missing");
  var row=rows.getFirst();String target=capture?"CAPTURED":"RELEASED";
  if(target.equals(row.get("status")))return;
  if(!"RESERVED".equals(row.get("status")))throw conflict();
  long amount=((Number)row.get("amount_minor")).longValue();
  db.update("update settlement_float set cash_minor=?,reserved_minor=? where id=1",capture?Math.subtractExact(b.cashMinor(),amount):b.cashMinor(),Math.subtractExact(b.reservedMinor(),amount));
  db.update("update settlement_reservation set status=? where operation_id=?",target,op);
 }
 public Reconciliation reconcile(String statementId,long reported){
  var b=lock();
  var old=db.query("select id,reported_minor,book_minor,difference_minor,status from settlement_reconciliation where statement_id=?",(r,n)->new Reconciliation(UUID.fromString(r.getString(1)),r.getLong(2),r.getLong(3),r.getLong(4),r.getString(5)),statementId);
  if(!old.isEmpty()){if(old.getFirst().reportedMinor()!=reported)throw conflict();return old.getFirst();}
  long ledgerCash=Math.negateExact(ledger.balanceOf("SYSTEM:PROVIDER_NGN_CASH",com.academy.paybridge.shared.money.Currency.NGN).minorUnits());
  if(ledgerCash!=b.cashMinor())throw new BusinessRuleException("FLOAT_LEDGER_MISMATCH","Float projection differs from its journal balance");
  long diff=Math.subtractExact(reported,b.cashMinor());var r=new Reconciliation(UUID.randomUUID(),reported,b.cashMinor(),diff,diff==0?"MATCHED":"BREAK");
  db.update("insert into settlement_reconciliation(id,statement_id,reported_minor,book_minor,difference_minor,status,created_at) values(?,?,?,?,?,?,?)",r.id(),statementId,reported,r.bookMinor(),diff,r.status(),java.sql.Timestamp.from(clock.instant()));
  return r;
 }
 private RuntimeException conflict(){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Reference or reservation conflicts with existing operation");}
}
```

### src/main/java/com/academy/paybridge/settlement/web/LocalTreasuryController.java

```java
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
```

### src/main/java/com/academy/paybridge/settlement/web/SettlementController.java

```java
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
```

## 2. Remittance

### src/main/java/com/academy/paybridge/remittance/api/RemittanceView.java

```java
package com.academy.paybridge.remittance.api;
import java.util.UUID;
public record RemittanceView(UUID id,UUID customerId,long sourceUsdMinor,long destinationNgnMinor,String status,String payoutReference,int attempt){}
```

### src/main/java/com/academy/paybridge/remittance/api/package-info.java

```java
@org.springframework.modulith.NamedInterface("api")
package com.academy.paybridge.remittance.api;
```

### src/main/java/com/academy/paybridge/remittance/package-info.java

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "customer::api", "compliance::api", "settlement::api", "fx::api", "ledger::api"})
package com.academy.paybridge.remittance;
```

### src/main/java/com/academy/paybridge/remittance/service/LocalPayoutAdapter.java

```java
package com.academy.paybridge.remittance.service;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
@Component
@Profile({"local","test"})
public class LocalPayoutAdapter {
 private final JdbcTemplate db; private final Clock clock;
 public LocalPayoutAdapter(JdbcTemplate db,Clock clock){this.db=db;this.clock=clock;}
 @Transactional public String submitOrVerify(String reference){
  // The fake provider is a separate persisted system of record. Stable reference is its idempotency key.
  db.queryForObject("select id from local_payout_lock where id=1 for update",Integer.class);
  var old=db.queryForList("select outcome from local_provider_payout where reference=?",reference);
  if(!old.isEmpty())return old.getFirst().get("outcome").toString();
  db.update("insert into local_provider_payout(reference,outcome,created_at) values(?,'SUCCESS',?)",reference,java.sql.Timestamp.from(clock.instant()));
  return "SUCCESS";
 }
 @Transactional public void setOutcome(String reference,String outcome){
  db.queryForObject("select id from local_payout_lock where id=1 for update",Integer.class);
  var old=db.queryForList("select outcome from local_provider_payout where reference=?",reference);
  if(old.isEmpty())db.update("insert into local_provider_payout(reference,outcome,created_at) values(?,?,?)",reference,outcome,java.sql.Timestamp.from(clock.instant()));
  else {
   String prior=old.getFirst().get("outcome").toString();
   if(!prior.equals("UNKNOWN") && !prior.equals(outcome))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Terminal fake provider outcomes cannot be changed");
   db.update("update local_provider_payout set outcome=? where reference=?",outcome,reference);
  }
 }
}
```

### src/main/java/com/academy/paybridge/remittance/service/LocalPayoutWorker.java

```java
package com.academy.paybridge.remittance.service;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import com.academy.paybridge.remittance.api.RemittanceView;
import java.util.UUID;
@Component
@Profile({"local","test"})
public class LocalPayoutWorker {
 private final RemittanceService service;private final LocalPayoutAdapter adapter;
 public LocalPayoutWorker(RemittanceService service,LocalPayoutAdapter adapter){this.service=service;this.adapter=adapter;}
 // No surrounding transaction. Submit/verify commits before the local financial transition.
 public RemittanceView process(UUID id){
  var r=service.inspect(id);if(!r.status().equals("PAYOUT_PENDING"))return r;
  var outcome=adapter.submitOrVerify(r.payoutReference());
  return service.finish(id,r.payoutReference(),outcome);
 }
 public void recover(){for(var r:service.pending())process(r.id());}
}
```

### src/main/java/com/academy/paybridge/remittance/service/RemittanceService.java

```java
package com.academy.paybridge.remittance.service;
import java.time.*;
import java.util.*;
import com.academy.paybridge.remittance.api.RemittanceView;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.fx.api.FxApi;
import com.academy.paybridge.settlement.api.SettlementApi;
import com.academy.paybridge.ledger.api.*;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.shared.exception.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@org.springframework.context.annotation.Profile({"local","test"})
@Service
@Transactional
public class RemittanceService {
 private final com.academy.paybridge.compliance.api.ComplianceApi compliance;private final JdbcTemplate db;private final CustomerApi customers;private final FxApi fx;private final SettlementApi settlement;private final LedgerApi ledger;private final Clock clock;
 public RemittanceService(JdbcTemplate db,CustomerApi customers,FxApi fx,SettlementApi settlement,LedgerApi ledger,Clock clock,com.academy.paybridge.compliance.api.ComplianceApi compliance){this.compliance=compliance;this.db=db;this.customers=customers;this.fx=fx;this.settlement=settlement;this.ledger=ledger;this.clock=clock;}
 public RemittanceView create(String key,long source,String name,String bank,String number){
  UUID owner=customers.currentCustomerId();
  // A customer row serializes concurrent creates and the remittance daily authorization.
  db.queryForObject("select customer_id from customer_identity where customer_id=? for update",UUID.class,owner);
  var old=db.queryForList("select * from remittance where customer_id=? and idempotency_key=?",owner,key);
  if(!old.isEmpty()){
   var r=old.getFirst();
   if(((Number)r.get("source_usd_minor")).longValue()!=source || !name.equals(r.get("beneficiary_name")) || !bank.equals(r.get("bank_code")) || !number.equals(r.get("account_number")))throw conflict("Idempotency key reused with different payload");
   return get(UUID.fromString(r.get("id").toString()));
  }
  if(!customers.getCustomer(owner).kycVerified())throw new BusinessRuleException("KYC_REQUIRED","Verify KYC before creating a remittance");
  if(source<=100 || source>1000000)throw new BusinessRuleException("REMITTANCE_LIMIT","Demo corridor permits more than USD 1 and at most USD 10000 per remittance");
  UUID id=UUID.randomUUID();String ref="rm-"+id+"-1";
  db.update("insert into remittance(id,customer_id,idempotency_key,source_usd_minor,destination_ngn_minor,beneficiary_name,bank_code,account_number,status,payout_reference,attempt,created_at) values(?,?,?,?,0,?,?,?,'AWAITING_FUNDING',?,1,?)",id,owner,key,source,name,bank,number,ref,java.sql.Timestamp.from(clock.instant()));
  return get(id);
 }
 public RemittanceView get(UUID id){var v=load(id,false);customers.requireOwner(v.customerId());return v;}
 public RemittanceView inspect(UUID id){return load(id,false);}
 private RemittanceView load(UUID id,boolean lock){
  var rows=db.query("select * from remittance where id=?"+(lock?" for update":""),(r,n)->new RemittanceView(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("customer_id")),r.getLong("source_usd_minor"),r.getLong("destination_ngn_minor"),r.getString("status"),r.getString("payout_reference"),r.getInt("attempt")),id);
  if(rows.isEmpty())throw new ResourceNotFoundException("Remittance",id);return rows.getFirst();
 }
 // Called only by the profile-restricted treasury local adapter; never a customer declaration of funding.
 public RemittanceView confirmLocalFunding(UUID id,String fundingId,long amount){
  var r=load(id,true);
  if(r.sourceUsdMinor()!=amount)throw new BusinessRuleException("FUNDING_MISMATCH","USD amount must match remittance");
  var receipt=db.queryForList("select remittance_id,amount_minor from remittance_funding where provider='LOCAL' and provider_reference=?",fundingId);
  if(!receipt.isEmpty()){
   if(!id.toString().equals(receipt.getFirst().get("remittance_id").toString()) || ((Number)receipt.getFirst().get("amount_minor")).longValue()!=amount)throw conflict("Funding reference already claimed");
   return r;
  }
  if(!r.status().equals("AWAITING_FUNDING"))throw new BusinessRuleException("FUNDING_STATE","Remittance already funded");
  db.update("insert into remittance_funding(provider,provider_reference,remittance_id,amount_minor,created_at) values('LOCAL',?,?,?,?)",fundingId,id,amount,java.sql.Timestamp.from(clock.instant()));
  ledger.post(new PostJournalCommand(id+":fund",JournalType.REMITTANCE_FUNDED,"Local simulated USD funding",List.of(
   EntryLine.debit("SYSTEM:PROVIDER_USD_CASH",Money.usd(amount)),EntryLine.credit(usd(id),Money.usd(amount)))));
  db.update("update remittance set status='FUNDED' where id=?",id);return load(id,false);
 }
 public RemittanceView convertAndQueue(UUID id,UUID quote){
  // Lock in the same customer->operation order used by every customer authorization.
  UUID owner=customers.currentCustomerId();
  db.queryForObject("select customer_id from customer_identity where customer_id=? for update",UUID.class,owner);
  var r=load(id,true);customers.requireOwner(r.customerId());
  if(!r.status().equals("FUNDED")){
   UUID used=db.queryForObject("select quote_id from remittance where id=?",UUID.class,id);
   if(quote.equals(used))return r;
   throw new BusinessRuleException("REMITTANCE_STATE","Only funded remittances can be converted");
  }
  if(!customers.getCustomer(owner).kycVerified())throw new BusinessRuleException("KYC_REQUIRED","Verified KYC required");
  Instant day=clock.instant().atZone(ZoneId.of("Africa/Lagos")).toLocalDate().atStartOfDay(ZoneId.of("Africa/Lagos")).toInstant();
  Long used=db.queryForObject("select coalesce(sum(source_usd_minor),0) from remittance where customer_id=? and converted_at>=?",Long.class,owner,java.sql.Timestamp.from(day));
  if(Math.addExact(used==null?0:used,r.sourceUsdMinor())>2000000)throw new BusinessRuleException("DAILY_REMITTANCE_LIMIT","Demo USD 20000 daily converted limit exceeded");
  var q=fx.accept(quote,id,owner,r.sourceUsdMinor());
  settlement.reserve(id,q.destinationMinor());
  String beneficiary=db.queryForObject("select beneficiary_name from remittance where id=?",String.class,id);
  var screening=compliance.screen(new com.academy.paybridge.compliance.api.ScreeningRequest("rm-"+id,owner,true,beneficiary,Money.ngn(q.destinationMinor())));
  if(screening.decision()!=com.academy.paybridge.compliance.api.Decision.ALLOW)
    throw new BusinessRuleException("COMPLIANCE_REJECTED",String.join(";",screening.reasons()));
  var sourceLines=new ArrayList<EntryLine>();
  sourceLines.add(EntryLine.debit(usd(id),Money.usd(r.sourceUsdMinor())));
  sourceLines.add(EntryLine.credit("SYSTEM:FX_POSITION_USD",Money.usd(r.sourceUsdMinor()-q.sourceFeeMinor())));
  if(q.sourceFeeMinor()>0)sourceLines.add(EntryLine.credit("SYSTEM:FX_FEE_REVENUE_USD",Money.usd(q.sourceFeeMinor())));
  ledger.post(new PostJournalCommand(id+":fx-usd",JournalType.FX_SOURCE,"USD conversion leg",sourceLines));
  ledger.post(new PostJournalCommand(id+":fx-ngn",JournalType.FX_TARGET,"NGN conversion leg",List.of(
   EntryLine.debit("SYSTEM:FX_POSITION_NGN",Money.ngn(q.destinationMinor())),EntryLine.credit(ngn(id),Money.ngn(q.destinationMinor())))));
  reserveJournal(r,q.destinationMinor());
  db.update("update remittance set quote_id=?,destination_ngn_minor=?,status='PAYOUT_PENDING',converted_at=? where id=?",quote,q.destinationMinor(),java.sql.Timestamp.from(clock.instant()),id);
  return load(id,false);
 }
 private void reserveJournal(RemittanceView r,long amount){
  ledger.post(new PostJournalCommand(r.payoutReference()+":reserve",JournalType.REMITTANCE_RESERVED,"Reserve payout payable",List.of(
   EntryLine.debit(ngn(r.id()),Money.ngn(amount)),EntryLine.credit(clearing(r.id()),Money.ngn(amount)))));
 }
 public RemittanceView retry(UUID id){
  var r=load(id,true);customers.requireOwner(r.customerId());
  if(!r.status().equals("PAYOUT_FAILED"))throw new BusinessRuleException("RETRY_STATE","Only definitively failed payouts can be retried");
  // Use one reservation per attempt; the previous failed attempt remains immutable evidence.
  int attempt=Math.incrementExact(r.attempt());String ref="rm-"+id+"-"+attempt;
  UUID reservation=reservationId(id,attempt);
  settlement.reserve(reservation,r.destinationNgnMinor());
  var next=new RemittanceView(id,r.customerId(),r.sourceUsdMinor(),r.destinationNgnMinor(),"PAYOUT_PENDING",ref,attempt);
  reserveJournal(next,r.destinationNgnMinor());
  db.update("update remittance set status='PAYOUT_PENDING',attempt=?,payout_reference=? where id=?",attempt,ref,id);return next;
 }
 public RemittanceView finish(UUID id,String expectedReference,String outcome){
  var r=load(id,true);
  if(!r.status().equals("PAYOUT_PENDING") || !r.payoutReference().equals(expectedReference))return r;
  UUID reservation=reservationId(id,r.attempt());
  if(outcome.equals("UNKNOWN"))return r;
  if(outcome.equals("SUCCESS")){
   settlement.capture(reservation);
   ledger.post(new PostJournalCommand(r.payoutReference()+":paid",JournalType.REMITTANCE_PAID,"Local simulated NGN payout",List.of(
    EntryLine.debit(clearing(id),Money.ngn(r.destinationNgnMinor())),EntryLine.credit("SYSTEM:PROVIDER_NGN_CASH",Money.ngn(r.destinationNgnMinor())))));
   db.update("update remittance set status='PAID_OUT' where id=?",id);
  }else if(outcome.equals("FAILED")){
   settlement.release(reservation);
   ledger.post(new PostJournalCommand(r.payoutReference()+":release",JournalType.REMITTANCE_RELEASED,"Payout failed; NGN remains payable",List.of(
    EntryLine.debit(clearing(id),Money.ngn(r.destinationNgnMinor())),EntryLine.credit(ngn(id),Money.ngn(r.destinationNgnMinor())))));
   db.update("update remittance set status='PAYOUT_FAILED' where id=?",id);
  }else throw new IllegalArgumentException("Unknown provider outcome");
  return load(id,false);
 }
 public List<RemittanceView> pending(){return db.query("select id from remittance where status='PAYOUT_PENDING'",(r,n)->load(UUID.fromString(r.getString(1)),false));}
 public static UUID reservationId(UUID id,int attempt){return attempt==1?id:UUID.nameUUIDFromBytes((id+":"+attempt).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private static String usd(UUID id){return "RM:"+id+":USD";}
 private static String ngn(UUID id){return "RM:"+id+":NGN";}
 private static String clearing(UUID id){return "RM:"+id+":CLEAR";}
 private RuntimeException conflict(String detail){return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,detail);}
}
```

### src/main/java/com/academy/paybridge/remittance/web/LocalRemittanceController.java

```java
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
```

### src/main/java/com/academy/paybridge/remittance/web/RemittanceController.java

```java
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
```

## 3. FX

### src/main/java/com/academy/paybridge/fx/api/FxApi.java

```java
package com.academy.paybridge.fx.api;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
public interface FxApi {
 record Quote(UUID id,UUID customerId,long sourceMinor,long sourceFeeMinor,long destinationMinor,BigDecimal rate,Instant expiresAt,String status){}
 Quote accept(UUID quoteId,UUID operationId,UUID customerId,long sourceMinor);
}
```

### src/main/java/com/academy/paybridge/fx/api/package-info.java

```java
@org.springframework.modulith.NamedInterface("api")
package com.academy.paybridge.fx.api;
```

### src/main/java/com/academy/paybridge/fx/package-info.java

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared", "customer::api", "ledger::api"})
package com.academy.paybridge.fx;
```

### src/main/java/com/academy/paybridge/fx/service/FxService.java

```java
package com.academy.paybridge.fx.service;
import com.academy.paybridge.fx.api.FxApi;
import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.shared.exception.*;
import java.util.*;
import java.time.*;
import java.math.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@org.springframework.context.annotation.Profile({"local","test"})
@Service
@Transactional
public class FxService implements FxApi {
 private final JdbcTemplate db;private final Clock clock;private final CustomerApi customers;
 private final BigDecimal rate;private final long fee;private final long ttl;
 public FxService(JdbcTemplate db,Clock clock,CustomerApi customers,
  @Value("${paybridge.fx.demo-ngn-per-usd:1500.00000000}") BigDecimal rate,
  @Value("${paybridge.fx.source-fee-cents:100}") long fee,
  @Value("${paybridge.fx.quote-ttl-seconds:60}") long ttl){
  if(rate.signum()<=0 || fee<0 || ttl<1)throw new IllegalArgumentException("Invalid FX configuration");
  this.db=db;this.clock=clock;this.customers=customers;this.rate=rate.setScale(8,RoundingMode.UNNECESSARY);this.fee=fee;this.ttl=ttl;
 }
 public Quote quote(long source){
  UUID owner=customers.currentCustomerId();
  if(!customers.getCustomer(owner).kycVerified())throw new BusinessRuleException("KYC_REQUIRED","Verified sender required");
  if(source<=fee || source>10000000)throw new BusinessRuleException("FX_AMOUNT_RANGE","Source must exceed fee and not exceed USD 100000");
  // Both supported currencies have exponent 2; cents * NGN/USD = kobo.
  long target=BigDecimal.valueOf(source-fee).multiply(rate).setScale(0,RoundingMode.DOWN).longValueExact();
  if(target<=0)throw new BusinessRuleException("FX_DUST","Destination rounds to zero");
  var q=new Quote(UUID.randomUUID(),owner,source,fee,target,rate,clock.instant().plusSeconds(ttl),"OPEN");
  db.update("insert into fx_quote(id,customer_id,source_minor,fee_minor,destination_minor,ngn_per_usd,expires_at,status,created_at) values(?,?,?,?,?,?,?,'OPEN',?)",q.id(),owner,source,fee,target,rate,java.sql.Timestamp.from(q.expiresAt()),java.sql.Timestamp.from(clock.instant()));
  return q;
 }
 public Quote get(UUID id){var q=load(id,false);customers.requireOwner(q.customerId());return q;}
 private Quote load(UUID id,boolean lock){
  var rows=db.query("select * from fx_quote where id=?"+(lock?" for update":""),(r,n)->new Quote(UUID.fromString(r.getString("id")),UUID.fromString(r.getString("customer_id")),r.getLong("source_minor"),r.getLong("fee_minor"),r.getLong("destination_minor"),r.getBigDecimal("ngn_per_usd"),r.getTimestamp("expires_at").toInstant(),r.getString("status")),id);
  if(rows.isEmpty())throw new ResourceNotFoundException("FX quote",id);return rows.getFirst();
 }
 public Quote accept(UUID id,UUID op,UUID customer,long source){
  var q=load(id,true);
  if(!q.customerId().equals(customer))throw new org.springframework.security.access.AccessDeniedException("Quote owner mismatch");
  if(q.sourceMinor()!=source)throw new BusinessRuleException("QUOTE_AMOUNT_MISMATCH","Funding amount differs from quote");
  UUID accepted=db.queryForObject("select accepted_operation_id from fx_quote where id=?",UUID.class,id);
  if("ACCEPTED".equals(q.status())){
   if(!op.equals(accepted))throw new BusinessRuleException("QUOTE_USED","Quote is already consumed");
   return q;
  }
  if(!clock.instant().isBefore(q.expiresAt()))throw new BusinessRuleException("QUOTE_EXPIRED","Obtain a fresh quote before conversion");
  db.update("update fx_quote set status='ACCEPTED',accepted_operation_id=? where id=?",op,id);
  return new Quote(q.id(),q.customerId(),q.sourceMinor(),q.sourceFeeMinor(),q.destinationMinor(),q.rate(),q.expiresAt(),"ACCEPTED");
 }
}
```

### src/main/java/com/academy/paybridge/fx/web/FxController.java

```java
package com.academy.paybridge.fx.web;
import com.academy.paybridge.fx.api.FxApi;
import com.academy.paybridge.fx.service.FxService;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@org.springframework.context.annotation.Profile({"local","test"})
@RestController
@RequestMapping("/api/v1/fx/quotes")
@PreAuthorize("hasRole('CUSTOMER')")
public class FxController {
 private final FxService service;
 public FxController(FxService service){this.service=service;}
 public record Request(@Positive long sourceUsdMinor){}
 @PostMapping public FxApi.Quote quote(@Valid @RequestBody Request r){return service.quote(r.sourceUsdMinor());}
 @GetMapping("/{id}") public FxApi.Quote get(@PathVariable UUID id){return service.get(id);}
}
```

## 4. Authentication and ownership

### src/main/java/com/academy/paybridge/shared/security/CurrentActor.java

```java
package com.academy.paybridge.shared.security;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class CurrentActor {
 public Jwt jwt() {
  var a=SecurityContextHolder.getContext().getAuthentication();
  if(a==null || !(a.getPrincipal() instanceof Jwt j)) throw new AccessDeniedException("A customer access token is required");
  return j;
 }
 public String subject(){return jwt().getSubject();}
 public String issuer(){return jwt().getIssuer().toString();}
 public boolean hasRole(String role){
  var a=SecurityContextHolder.getContext().getAuthentication();
  return a!=null && a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));
 }
 public void requireRole(String role){if(!hasRole(role))throw new AccessDeniedException("Required role: "+role);}
}
```

### src/main/java/com/academy/paybridge/shared/security/SecurityConfig.java

```java
package com.academy.paybridge.shared.security;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity http, Environment env) throws Exception {
  // Bearer tokens only: no cookie-authenticated API sessions.
  http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  http.authorizeHttpRequests(a->{
   a.requestMatchers("/actuator/health","/error","/api/v1/webhooks/paystack").permitAll();
   if(env.acceptsProfiles(org.springframework.core.env.Profiles.of("local","test")))
    a.requestMatchers("/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll();
   a.requestMatchers("/api/v1/settlements/**").hasRole("TREASURY");
   a.requestMatchers("/api/v1/local/**").hasRole("TREASURY");
   a.anyRequest().authenticated();
  });
  var converter=new JwtAuthenticationConverter();
  converter.setJwtGrantedAuthoritiesConverter(jwt->{
   var result=new ArrayList<org.springframework.security.core.GrantedAuthority>();
   result.addAll(new JwtGrantedAuthoritiesConverter().convert(jwt));
   Object realm=jwt.getClaim("realm_access");
   if(realm instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> roles)
    roles.stream().filter(String.class::isInstance).map(String.class::cast)
     .filter(Set.of("CUSTOMER","TREASURY","COMPLIANCE","SUPPORT")::contains)
     .forEach(r->result.add(new SimpleGrantedAuthority("ROLE_"+r)));
   return result;
  });
  http.oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)));
  return http.build();
 }
}
```

### src/main/java/com/academy/paybridge/customer/api/CustomerApi.java

```java
package com.academy.paybridge.customer.api;

import java.util.Optional;
import java.util.UUID;

public interface CustomerApi {

    CustomerSummary getCustomer(UUID id);
    UUID currentCustomerId();
    void requireOwner(UUID customerId);
    boolean exists(UUID id);
    Optional<NextOfKinSummary> findNextOfKin(UUID customerId);
}
```

### src/main/java/com/academy/paybridge/customer/service/DefaultCustomerApi.java

```java
package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.api.CustomerApi;
import com.academy.paybridge.customer.api.CustomerSummary;
import com.academy.paybridge.customer.api.NextOfKinSummary;
import com.academy.paybridge.customer.repository.CustomerRepository;
import com.academy.paybridge.customer.repository.NextOfKinRepository;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
class DefaultCustomerApi implements CustomerApi {

    private final CustomerRepository customers;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.academy.paybridge.shared.security.CurrentActor actor;
    private final NextOfKinRepository nextOfKin;

    DefaultCustomerApi(CustomerRepository customers, NextOfKinRepository nextOfKin, org.springframework.jdbc.core.JdbcTemplate jdbc,
                       com.academy.paybridge.shared.security.CurrentActor actor) {
        this.jdbc=jdbc; this.actor=actor;
        this.customers = customers;
        this.nextOfKin = nextOfKin;
    }

    @Override
    public CustomerSummary getCustomer(UUID id) {
        return customers.findById(id)
                .map(c -> new CustomerSummary(c.getId(), c.fullName(), c.getEmail(), c.isKycVerified()))
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Override
    public UUID currentCustomerId() {
        var ids=jdbc.query("select customer_id from customer_identity where issuer=? and subject=?",
            (rs,n)->UUID.fromString(rs.getString(1)),actor.issuer(),actor.subject());
        if(ids.isEmpty()) throw new com.academy.paybridge.shared.exception.BusinessRuleException("CUSTOMER_NOT_LINKED","Onboard your customer profile first.");
        return ids.getFirst();
    }
    @Override
    public void requireOwner(UUID id) {
        if(!currentCustomerId().equals(id)) throw new org.springframework.security.access.AccessDeniedException("Customer ownership required");
    }
    @Override
    public boolean exists(UUID id) { return customers.existsById(id); }

    @Override
    public Optional<NextOfKinSummary> findNextOfKin(UUID customerId) {
        return nextOfKin.findByCustomerId(customerId)
                .map(n -> new NextOfKinSummary(n.getNextOfKinCustomerId(), n.getRelationship().name()));
    }
}
```

### src/main/java/com/academy/paybridge/customer/service/CustomerService.java

```java
package com.academy.paybridge.customer.service;

import com.academy.paybridge.customer.api.CustomerOnboarded;
import com.academy.paybridge.customer.domain.Customer;
import com.academy.paybridge.customer.domain.NextOfKin;
import com.academy.paybridge.customer.domain.Relationship;
import com.academy.paybridge.customer.repository.CustomerRepository;
import com.academy.paybridge.customer.repository.NextOfKinRepository;
import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final com.academy.paybridge.shared.security.CurrentActor actor;
    private final NextOfKinRepository nextOfKin;
    private final ApplicationEventPublisher events;

    public CustomerService(CustomerRepository customers, NextOfKinRepository nextOfKin,
                           ApplicationEventPublisher events, org.springframework.jdbc.core.JdbcTemplate jdbc,
                           com.academy.paybridge.shared.security.CurrentActor actor) {
        this.jdbc=jdbc; this.actor=actor;
        this.customers = customers;
        this.nextOfKin = nextOfKin;
        this.events = events;
    }

    @Transactional
    public Customer onboard(OnboardCommand cmd) {
        if(!actor.hasRole("CUSTOMER")) throw new org.springframework.security.access.AccessDeniedException("Customer role required");
        if(Boolean.FALSE.equals(actor.jwt().getClaimAsBoolean("email_verified")))
            throw new org.springframework.security.access.AccessDeniedException("Verified identity email required");
        String email=actor.jwt().getClaimAsString("email");
        if(email==null || !Boolean.TRUE.equals(actor.jwt().getClaimAsBoolean("email_verified")) || !email.equalsIgnoreCase(cmd.email().trim()))
            throw new org.springframework.security.access.AccessDeniedException("Use the verified identity email");
        Integer linked=jdbc.queryForObject("select count(*) from customer_identity where issuer=? and subject=?",Integer.class,actor.issuer(),actor.subject());
        if(linked!=null && linked>0) throw new BusinessRuleException("CUSTOMER_ALREADY_LINKED","Identity already onboarded");
        if (customers.existsByEmailIgnoreCase(cmd.email()))
            throw new BusinessRuleException("EMAIL_TAKEN", "A customer with this email already exists.");
        Customer saved = customers.save(
                Customer.onboard(cmd.firstName(), cmd.lastName(), cmd.email(), cmd.phoneNumber()));
        customers.flush();
        jdbc.update("insert into customer_identity(issuer,subject,customer_id) values(?,?,?)",actor.issuer(),actor.subject(),saved.getId());
        events.publishEvent(new CustomerOnboarded(saved.getId(), saved.fullName(), saved.getEmail()));
        return saved;
    }

    public Customer getOrThrow(UUID id) {
        return customers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    @Transactional
    public Customer verifyKyc(UUID id) {
        Customer customer = getOrThrow(id);
        customer.verifyKyc();
        return customer;
    }

    @Transactional
    public NextOfKin designateNextOfKin(UUID customerId, UUID nokCustomerId, Relationship relationship) {
        getOrThrow(customerId);
        getOrThrow(nokCustomerId);
        return nextOfKin.findByCustomerId(customerId)
                .map(existing -> { existing.change(nokCustomerId, relationship); return existing; })
                .orElseGet(() -> nextOfKin.save(NextOfKin.designate(customerId, nokCustomerId, relationship)));
    }

    public Optional<NextOfKin> findNextOfKin(UUID customerId) {
        return nextOfKin.findByCustomerId(customerId);
    }
}
```

### src/main/java/com/academy/paybridge/customer/web/CustomerController.java

```java
package com.academy.paybridge.customer.web;

import com.academy.paybridge.customer.service.CustomerService;
import com.academy.paybridge.customer.service.OnboardCommand;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customers", description = "Onboarding, KYC and next of kin")
public class CustomerController {

    private final CustomerService service;
    private final com.academy.paybridge.customer.api.CustomerApi customerApi;
    private final com.academy.paybridge.shared.security.CurrentActor actor;

    public CustomerController(CustomerService service, com.academy.paybridge.customer.api.CustomerApi customerApi,
                              com.academy.paybridge.shared.security.CurrentActor actor) {
        this.customerApi=customerApi; this.actor=actor; this.service = service; }

    @Operation(summary = "Onboard a customer",
            description = "Creates the customer with KYC status PENDING. A PayBridge NGN account is opened "
                    + "in the onboarding transaction by the account module.")
    @ApiResponse(responseCode = "201", description = "Customer created")
    @PostMapping
    ResponseEntity<CustomerResponse> onboard(@Valid @RequestBody OnboardCustomerRequest req) {
        var customer = service.onboard(
                new OnboardCommand(req.firstName(), req.lastName(), req.email(), req.phoneNumber()));
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.getId()))
                .body(CustomerResponse.from(customer));
    }

    @GetMapping("/me")
    CustomerResponse me() { return CustomerResponse.from(service.getOrThrow(customerApi.currentCustomerId())); }

    @Operation(summary = "Get a customer")
    @GetMapping("/{customerId}")
    CustomerResponse get(@PathVariable UUID customerId) {
        customerApi.requireOwner(customerId);
        return CustomerResponse.from(service.getOrThrow(customerId));
    }

    @Operation(summary = "Mark a customer's KYC as verified",
            description = "A REJECTED customer cannot be verified (422, KYC_ALREADY_REJECTED).")
    @PostMapping("/{customerId}/kyc/verify")
    CustomerResponse verifyKyc(@PathVariable UUID customerId) {
        actor.requireRole("COMPLIANCE");
        return CustomerResponse.from(service.verifyKyc(customerId));
    }

    @Operation(summary = "Designate or change a customer's next of kin",
            description = "Upsert: one next of kin per customer. Used by the transfer routing rule.")
    @PutMapping("/{customerId}/next-of-kin")
    NextOfKinResponse designate(@PathVariable UUID customerId, @Valid @RequestBody DesignateNextOfKinRequest req) {
        customerApi.requireOwner(customerId);
        return NextOfKinResponse.from(
                service.designateNextOfKin(customerId, req.nextOfKinCustomerId(), req.relationship()));
    }

    @Operation(summary = "Get a customer's next of kin")
    @GetMapping("/{customerId}/next-of-kin")
    NextOfKinResponse nextOfKin(@PathVariable UUID customerId) {
        customerApi.requireOwner(customerId);
        return service.findNextOfKin(customerId)
                .map(NextOfKinResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("NextOfKin", customerId));
    }

    @Hidden                                   // dev-only check; keep it out of the docs
    @GetMapping("/hello")
    String hello() { return "virtual=" + Thread.currentThread().isVirtual(); }
}
```

### src/main/java/com/academy/paybridge/account/web/AccountController.java

```java
package com.academy.paybridge.account.web;

import com.academy.paybridge.account.api.AccountApi;
import com.academy.paybridge.account.api.AccountSummary;
import com.academy.paybridge.shared.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController {

    private final AccountApi accountApi;
    private final com.academy.paybridge.customer.api.CustomerApi customers;

    AccountController(AccountApi accountApi, com.academy.paybridge.customer.api.CustomerApi customers) {
        this.accountApi=accountApi; this.customers=customers;
    }

    @GetMapping("/by-customer/{customerId}")
    AccountSummary byCustomer(@PathVariable UUID customerId) {
        customers.requireOwner(customerId);
        return accountApi.findPrimaryAccount(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", customerId));
    }

    @GetMapping("/by-customer/{customerId}/balance")
    BalanceResponse balance(@PathVariable UUID customerId) {
        customers.requireOwner(customerId);
        return accountApi.findBalance(customerId).map(BalanceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Balance", customerId));
    }
}
```

### src/main/java/com/academy/paybridge/transfer/web/TransferController.java

```java
package com.academy.paybridge.transfer.web;

import com.academy.paybridge.shared.exception.BusinessRuleException;
import com.academy.paybridge.shared.money.Money;
import com.academy.paybridge.transfer.service.InitiateTransferCommand;
import com.academy.paybridge.transfer.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Initiate transfers; routing to next of kin and external payouts are applied automatically")
class TransferController {

    private final TransferService service;
    private final com.academy.paybridge.customer.api.CustomerApi customers;

    TransferController(TransferService service, com.academy.paybridge.customer.api.CustomerApi customers) {
        this.service=service; this.customers=customers;
    }

    @Operation(summary = "Initiate a transfer",
            description = """
                    Routing: if the recipient's PayBridge balance is at or above the configured threshold and they
                    have a next of kin with an account, the money goes to the next of kin instead
                    (`routingOutcome` = REDIRECTED_TO_NEXT_OF_KIN). External destinations are paid out via the gateway.

                    Send the same `Idempotency-Key` to safely retry: you get the original transfer back.""")
    @ApiResponse(responseCode = "201", description = "Completed immediately (internal transfer, or payout confirmed)")
    @ApiResponse(responseCode = "202", description = "Accepted; payout still processing, final status arrives by webhook")
    @ApiResponse(responseCode = "422", description = "Rejected or failed; the body shows the status and failureReason")
    @PostMapping
    ResponseEntity<TransferResponse> initiate(
            @Parameter(description = "Unique per logical transfer, 1 to 64 characters", example = "demo-001", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiateTransferRequest req) {

        if (idempotencyKey.isBlank() || idempotencyKey.length() > 64)
            throw new BusinessRuleException("BAD_IDEMPOTENCY_KEY", "Idempotency-Key must be 1 to 64 characters.");

        customers.requireOwner(req.senderCustomerId());
        var transfer = service.initiate(new InitiateTransferCommand(idempotencyKey, req.senderCustomerId(),
                req.recipientCustomerId(), new Money(req.amountMinor(), req.currency()), req.narration()));
        var body = TransferResponse.from(transfer);

        return switch (transfer.getStatus()) {
            case COMPLETED -> ResponseEntity.created(URI.create("/api/v1/transfers/" + transfer.getId())).body(body);
            case PENDING, PROCESSING -> ResponseEntity.accepted()
                    .location(URI.create("/api/v1/transfers/" + transfer.getId())).body(body);
            default -> ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        };
    }

    @Operation(summary = "Get a transfer and its current status")
    @GetMapping("/{id}")
    TransferResponse get(@PathVariable UUID id) {
        var t=service.getOrThrow(id); customers.requireOwner(t.getSenderCustomerId());
        return TransferResponse.from(t);
    }
}
```

### src/main/java/com/academy/paybridge/shared/exception/GlobalExceptionHandler.java

```java
package com.academy.paybridge.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Spring's own MVC exceptions (405, 415, missing header...) are handled by the parent class.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        pd.setProperty("errors", errors);
        return handleExceptionInternal(ex, pd, headers, status, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusiness(BusinessRuleException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(Exception ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with current state. Check for duplicates and retry.");
    }

    @ExceptionHandler(PaybridgeException.class)       // e.g. gateway errors; BusinessRule/NotFound above are more specific
    ProblemDetail handleOther(PaybridgeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setProperty("code", ex.getCode());
        return pd;
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ProblemDetail denied(Exception ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied"); }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ProblemDetail responseStatus(org.springframework.web.server.ResponseStatusException ex) {
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason()==null ? "Request rejected" : ex.getReason());
    }

    @ExceptionHandler(ArithmeticException.class)
    ProblemDetail overflow(ArithmeticException ex) { return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,"Amount exceeds supported precision or range"); }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong.");
    }
}
```

## 5. Schema and configuration

### src/main/resources/db/migration/V7__identity_settlement_fx_remittance.sql

```sql
CREATE TABLE customer_identity (
 issuer VARCHAR(300) NOT NULL, subject VARCHAR(150) NOT NULL, customer_id UUID NOT NULL UNIQUE REFERENCES customer(id),
 PRIMARY KEY(issuer,subject)
);
CREATE TABLE settlement_float (
 id INTEGER PRIMARY KEY CHECK (id=1), cash_minor BIGINT NOT NULL CHECK(cash_minor>=0),
 reserved_minor BIGINT NOT NULL CHECK(reserved_minor>=0 AND reserved_minor<=cash_minor)
);
INSERT INTO settlement_float VALUES(1,0,0);
CREATE TABLE settlement_topup(reference VARCHAR(64) PRIMARY KEY,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE settlement_reservation(operation_id UUID PRIMARY KEY,amount_minor BIGINT NOT NULL CHECK(amount_minor>0),status VARCHAR(20) NOT NULL CHECK(status IN ('RESERVED','CAPTURED','RELEASED')));
CREATE TABLE settlement_reconciliation(id UUID PRIMARY KEY,statement_id VARCHAR(100) NOT NULL UNIQUE,reported_minor BIGINT NOT NULL CHECK(reported_minor>=0),book_minor BIGINT NOT NULL,difference_minor BIGINT NOT NULL,status VARCHAR(10) NOT NULL CHECK(status IN ('MATCHED','BREAK')),created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE fx_quote(id UUID PRIMARY KEY,customer_id UUID NOT NULL,source_minor BIGINT NOT NULL CHECK(source_minor>0),fee_minor BIGINT NOT NULL CHECK(fee_minor>=0 AND fee_minor<source_minor),destination_minor BIGINT NOT NULL CHECK(destination_minor>0),ngn_per_usd NUMERIC(24,8) NOT NULL CHECK(ngn_per_usd>0),expires_at TIMESTAMPTZ NOT NULL,status VARCHAR(10) NOT NULL CHECK(status IN ('OPEN','ACCEPTED')),accepted_operation_id UUID UNIQUE,created_at TIMESTAMPTZ NOT NULL);
CREATE TABLE remittance(id UUID PRIMARY KEY,customer_id UUID NOT NULL,idempotency_key VARCHAR(64) NOT NULL,source_usd_minor BIGINT NOT NULL CHECK(source_usd_minor>0),destination_ngn_minor BIGINT NOT NULL CHECK(destination_ngn_minor>=0),beneficiary_name VARCHAR(100) NOT NULL,bank_code VARCHAR(6) NOT NULL,account_number VARCHAR(10) NOT NULL,status VARCHAR(30) NOT NULL CHECK(status IN ('AWAITING_FUNDING','FUNDED','PAYOUT_PENDING','PAID_OUT','PAYOUT_FAILED')),quote_id UUID UNIQUE,payout_reference VARCHAR(60) NOT NULL UNIQUE,attempt INTEGER NOT NULL CHECK(attempt>0),created_at TIMESTAMPTZ NOT NULL,converted_at TIMESTAMPTZ,UNIQUE(customer_id,idempotency_key));
CREATE INDEX ix_remittance_work ON remittance(status);
CREATE INDEX ix_remittance_customer_day ON remittance(customer_id,converted_at);
CREATE TABLE remittance_funding(provider VARCHAR(20) NOT NULL,provider_reference VARCHAR(80) NOT NULL,remittance_id UUID NOT NULL UNIQUE REFERENCES remittance(id),amount_minor BIGINT NOT NULL CHECK(amount_minor>0),created_at TIMESTAMPTZ NOT NULL,PRIMARY KEY(provider,provider_reference));
CREATE TABLE local_payout_lock(id INTEGER PRIMARY KEY);
INSERT INTO local_payout_lock VALUES(1);
CREATE TABLE local_provider_payout(reference VARCHAR(60) PRIMARY KEY,outcome VARCHAR(10) NOT NULL CHECK(outcome IN ('SUCCESS','FAILED','UNKNOWN')),created_at TIMESTAMPTZ NOT NULL);
```

### pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0</version>
        <relativePath/>
    </parent>

    <groupId>com.academy</groupId>
    <artifactId>paybridge</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>paybridge</name>
    <description>Money transfer, cross-border settlement and USD to NGN remittance</description>

    <properties>
        <java.version>21</java.version>
        <!-- Temporary security overrides for Spring Boot 4.1.0 (Mend scan, 2026-10-01).
             Remove when upgrading to Spring Boot 4.1.1 or later. -->
        <tomcat.version>11.0.26</tomcat.version>
        <spring-framework.version>7.0.9</spring-framework.version>
        <jackson-bom.version>3.1.7</jackson-bom.version>
        <logback.version>1.5.38</logback.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>2.1.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
        <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-restclient</artifactId></dependency>
        <!-- Source: https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>3.1.0</version>
            <scope>compile</scope>
        </dependency>

        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-flyway</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
        <dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency>

        <dependency><groupId>org.springframework.modulith</groupId><artifactId>spring-modulith-starter-core</artifactId></dependency>
        <dependency><groupId>org.springframework.modulith</groupId><artifactId>spring-modulith-events-api</artifactId></dependency>

        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-webmvc-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>org.springframework.modulith</groupId><artifactId>spring-modulith-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### src/main/resources/application.yml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER:http://localhost:18080/realms/paybridge}
          audiences: paybridge-api
  application:
    name: paybridge
  threads:
    virtual:
      enabled: true
  datasource:
    url: ${DB_URL:jdbc:postgresql://127.0.0.1:15432/paybridge}
    username: ${DB_USERNAME:paybridge}
    password: ${DB_PASSWORD:paybridge}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 3000
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  flyway:
    locations: classpath:db/migration

springdoc:
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: method
    tags-sorter: alpha
    display-request-duration: true
  api-docs:
    path: /v3/api-docs
  default-produces-media-type: application/json

server:
  port: 8080

paybridge:
  routing:
    next-of-kin-threshold-minor: 1000000000    # 10,000,000 NGN, expressed in KOBO
  gateway:
    mode: simulated
  paystack:
    base-url: https://api.paystack.co
    secret-key: ${PAYSTACK_SECRET_KEY:}
  compliance:
    max-per-transaction-minor: 500000000       # 5,000,000 NGN in kobo
    daily-limit-minor: 2000000000              # 20,000,000 NGN in kobo
  services:
    base-url: http://localhost:8081
# Local rates are illustrative; conversion is disabled outside local/test until a real adapter is selected.
```

### src/main/resources/application-local.yml

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD:paybridge-local-only}
  flyway:
    locations: classpath:db/migration
springdoc:
  swagger-ui:
    oauth:
      client-id: paybridge-swagger
      use-pkce-with-authorization-code-grant: true
paybridge:
  fx:
    demo-ngn-per-usd: 1500.00000000
    source-fee-cents: 100
    quote-ttl-seconds: 60
```

### compose.yml

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: paybridge
      POSTGRES_USER: paybridge
      POSTGRES_PASSWORD: ${DB_PASSWORD:-paybridge-local-only}
    ports: ["127.0.0.1:15432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U paybridge -d paybridge"]
      interval: 5s
      timeout: 3s
      retries: 10
  keycloak:
    image: quay.io/keycloak/keycloak:26.8.1
    command: ["start-dev", "--import-realm"]
    environment:
      KC_BOOTSTRAP_ADMIN_USERNAME: admin
      KC_BOOTSTRAP_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:?Set KEYCLOAK_ADMIN_PASSWORD locally}
      KC_HOSTNAME: http://localhost:18080
    ports: ["127.0.0.1:18080:8080"]
    volumes:
      - ./infra/keycloak:/opt/keycloak/data/import:ro
      - kcdata:/opt/keycloak/data
volumes:
  pgdata:
  kcdata:
```

### infra/keycloak/paybridge-realm.json

```json
{
  "realm": "paybridge",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "accessTokenLifespan": 300,
  "roles": {
    "realm": [
      {
        "name": "CUSTOMER"
      },
      {
        "name": "TREASURY"
      },
      {
        "name": "COMPLIANCE"
      },
      {
        "name": "SUPPORT"
      }
    ]
  },
  "clients": [
    {
      "clientId": "paybridge-api",
      "enabled": true,
      "bearerOnly": true,
      "protocol": "openid-connect"
    },
    {
      "clientId": "paybridge-swagger",
      "enabled": true,
      "publicClient": true,
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "redirectUris": [
        "http://localhost:8080/swagger-ui/oauth2-redirect.html"
      ],
      "webOrigins": [
        "http://localhost:8080"
      ],
      "attributes": {
        "pkce.code.challenge.method": "S256"
      },
      "protocolMappers": [
        {
          "name": "paybridge-audience",
          "protocol": "openid-connect",
          "protocolMapper": "oidc-audience-mapper",
          "consentRequired": false,
          "config": {
            "included.client.audience": "paybridge-api",
            "id.token.claim": "false",
            "access.token.claim": "true"
          }
        }
      ],
      "defaultClientScopes": [
        "profile",
        "email",
        "roles",
        "web-origins",
        "acr"
      ]
    }
  ]
}
```

### src/main/java/com/academy/paybridge/shared/config/OpenApiConfig.java

```java
package com.academy.paybridge.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Arrays;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paybridgeOpenApi(@org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return new OpenAPI().components(new io.swagger.v3.oas.models.Components()
          .addSecuritySchemes("oidc",new io.swagger.v3.oas.models.security.SecurityScheme()
            .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.OAUTH2)
            .flows(new io.swagger.v3.oas.models.security.OAuthFlows().authorizationCode(
              new io.swagger.v3.oas.models.security.OAuthFlow().authorizationUrl(issuer+"/protocol/openid-connect/auth")
              .tokenUrl(issuer+"/protocol/openid-connect/token")
              .scopes(new io.swagger.v3.oas.models.security.Scopes().addString("openid","OpenID Connect").addString("profile","Profile").addString("email","Email"))))))
          .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList("oidc"))
          .info(new Info()
                .title("PayBridge API")
                .version("v1")
                .description("""
                        Money transfer, cross-border settlement and USD to NGN remittance.

                        **All amounts are in minor units** (kobo for NGN, cents for USD): 500000 = NGN 5,000.00.

                        **Errors** use RFC 9457 `application/problem+json`. Business-rule failures (422) carry a
                        machine-readable `code`; validation failures (400) carry an `errors` map keyed by field.
                        """));
    }

    @Bean GroupedOpenApi allApi() {
        return GroupedOpenApi.builder().group("all").pathsToMatch("/api/**").build();
    }

    @Bean GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder().group("customers").pathsToMatch("/api/v1/customers/**").build();
    }

    @Bean GroupedOpenApi accountApi() {
        return GroupedOpenApi.builder().group("accounts").pathsToMatch("/api/v1/accounts/**").build();
    }

    @Bean GroupedOpenApi transferApi() {
        return GroupedOpenApi.builder().group("transfers").pathsToMatch("/api/v1/transfers/**").build();
    }

    /** Adds the error responses our GlobalExceptionHandler can produce to every operation. */
    @Bean
    OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            if (responses == null) return operation;

            responses.putIfAbsent("401", new ApiResponse().description("Missing, expired or invalid token"));
            responses.putIfAbsent("403", new ApiResponse().description("Role or resource ownership denied"));
            responses.putIfAbsent("409", new ApiResponse().description("Conflicting idempotency request or state"));
            responses.putIfAbsent("400", new ApiResponse().description("Validation failed (problem+json with an 'errors' map)"));
            responses.putIfAbsent("422", new ApiResponse().description("Business rule violated (problem+json with a 'code')"));
            responses.putIfAbsent("500", new ApiResponse().description("Unexpected error"));

            boolean hasPathVariable = Arrays.stream(handlerMethod.getMethod().getParameters())
                    .anyMatch(p -> p.isAnnotationPresent(PathVariable.class));
            if (hasPathVariable)
                responses.putIfAbsent("404", new ApiResponse().description("Resource not found (problem+json)"));
            return operation;
        };
    }
}
```

## 6. Actual HTTP tests

### src/test/java/com/academy/paybridge/WorkflowHttpTest.java

```java
package com.academy.paybridge;

import java.net.URI;
import java.net.http.*;
import java.security.*;
import java.security.interfaces.*;
import java.time.*;
import java.util.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(WorkflowHttpTest.Tokens.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkflowHttpTest {
 @LocalServerPort int port;
 @Autowired JdbcTemplate db;
 @org.springframework.test.context.DynamicPropertySource
 static void optionalPostgres(org.springframework.test.context.DynamicPropertyRegistry r){
  String url=System.getenv("PAYBRIDGE_TEST_DB_URL");
  if(url!=null && !url.isBlank()){
   r.add("spring.datasource.url",()->url);
   r.add("spring.datasource.username",()->System.getenv().getOrDefault("PAYBRIDGE_TEST_DB_USER","paybridge"));
   r.add("spring.datasource.password",()->System.getenv().getOrDefault("PAYBRIDGE_TEST_DB_PASSWORD","paybridge-test-only"));
   r.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
   r.add("spring.flyway.enabled",()->true);
   r.add("spring.sql.init.mode",()->"never");
  }
 }
 static KeyPair pair;
 static {try {var g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);pair=g.generateKeyPair();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
 @TestConfiguration static class Tokens {
  @Bean JwtDecoder decoder(){
   var d=NimbusJwtDecoder.withPublicKey((RSAPublicKey)pair.getPublic()).build();
   d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("https://issuer.test"),
     new JwtClaimValidator<List<String>>("aud",a->a!=null&&a.contains("paybridge-api"))));return d;
  }
 }
 final HttpClient http=HttpClient.newHttpClient();final JsonMapper json=JsonMapper.builder().build();
 static UUID alice,bob,remit;static String quote,payoutReference;
 String token(String sub,String role){return token(sub,role,"paybridge-api",Instant.now().plusSeconds(300));}
 String token(String sub,String role,String aud,Instant expiry){
  try {
   var c=new JWTClaimsSet.Builder().issuer("https://issuer.test").subject(sub).audience(aud)
    .issueTime(Date.from(Instant.now().minusSeconds(120))).expirationTime(Date.from(expiry))
    .claim("realm_access",Map.of("roles",List.of(role))).claim("email",sub+"@example.com").claim("email_verified",true).build();
   var j=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),c);j.sign(new RSASSASigner((RSAPrivateKey)pair.getPrivate()));return j.serialize();
  }catch(Exception e){throw new RuntimeException(e);}
 }
 record Result(int status,String body){String text(String name){return JsonMapper.builder().build().readTree(body).get(name).asText();}}
 Result call(String method,String path,String bearer,String body,String key){
  try {
   var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
   if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);
   b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body));
   var r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
   synchronized(WorkflowHttpTest.class){
    java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/endpoint-transcript.jsonl"),
     json.writeValueAsString(Map.of("method",method,"path",path,"status",r.statusCode(),"body",r.body()))+"\n",
     java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND);
   }
   return new Result(r.statusCode(),r.body());
  }catch(Exception e){throw new RuntimeException(e);}
 }
 Result post(String path,String user,String role,String body){return call("POST",path,token(user,role),body,null);}
 void status(Result r,int expected){assertThat(r.status()).as(r.body()).isEqualTo(expected);}
 String createBody(long amount){return "{\"sourceUsdMinor\":"+amount+",\"beneficiaryName\":\"Ada Obi\",\"bankCode\":\"058\",\"accountNumber\":\"0123456789\"}";}
 Result create(String key,long amount){return call("POST","/api/v1/remittances",token("alice","CUSTOMER"),createBody(amount),key);}
 String base(){return "/api/v1/remittances/"+remit;}
 String local(){return "/api/v1/local/remittances/"+remit;}
 @Test @Order(1) void rejectsMissingInvalidExpiredAndWrongAudience(){
  status(call("GET","/api/v1/settlements/float",null,null,null),401);
  status(call("GET","/api/v1/settlements/float","bad-token",null,null),401);
  status(call("GET","/api/v1/settlements/float",token("treasury","TREASURY","other-api",Instant.now().plusSeconds(300)),null,null),401);
  status(call("GET","/api/v1/settlements/float",token("treasury","TREASURY","paybridge-api",Instant.now().minusSeconds(120)),null,null),401);
 }
 @Test @Order(2) void onboardBindsIdentityAndProtectsKyc(){
  var a=post("/api/v1/customers","alice","CUSTOMER","{\"firstName\":\"Alice\",\"lastName\":\"Example\",\"email\":\"alice@example.com\",\"phoneNumber\":\"08031230001\"}");status(a,201);alice=UUID.fromString(a.text("id"));
  var b=post("/api/v1/customers","bob","CUSTOMER","{\"firstName\":\"Bob\",\"lastName\":\"Example\",\"email\":\"bob@example.com\",\"phoneNumber\":\"08031230002\"}");status(b,201);bob=UUID.fromString(b.text("id"));
  status(post("/api/v1/customers/"+alice+"/kyc/verify","alice","CUSTOMER",null),403);
  status(post("/api/v1/customers/"+alice+"/kyc/verify","reviewer","COMPLIANCE",null),200);
  status(post("/api/v1/customers/"+bob+"/kyc/verify","reviewer","COMPLIANCE",null),200);
  status(call("GET","/api/v1/customers/"+bob,token("alice","CUSTOMER"),null,null),403);
  status(call("GET","/api/v1/accounts/by-customer/"+bob+"/balance",token("alice","CUSTOMER"),null,null),403);
  status(call("GET","/api/v1/customers/me",token("alice","CUSTOMER"),null,null),200);
 }
 @Test @Order(3) void treasuryTopupsAreAuthorizedIdempotentAndReconciled(){
  status(post("/api/v1/local/settlements/topups","alice","CUSTOMER","{\"reference\":\"float-1\",\"amountMinor\":100000000}"),403);
  String body="{\"reference\":\"float-1\",\"amountMinor\":100000000}";
  var r=post("/api/v1/local/settlements/topups","treasury","TREASURY",body);status(r,200);assertThat(r.text("cashMinor")).isEqualTo("100000000");
  status(post("/api/v1/local/settlements/topups","treasury","TREASURY",body),200);
  status(post("/api/v1/local/settlements/topups","treasury","TREASURY","{\"reference\":\"float-1\",\"amountMinor\":1}"),409);
  var m=post("/api/v1/settlements/reconciliations","treasury","TREASURY","{\"statementId\":\"s1\",\"closingBalanceMinor\":100000000}");status(m,200);assertThat(m.text("status")).isEqualTo("MATCHED");
  var br=post("/api/v1/settlements/reconciliations","treasury","TREASURY","{\"statementId\":\"s2\",\"closingBalanceMinor\":99999999}");status(br,200);assertThat(br.text("status")).isEqualTo("BREAK");
 }
 @Test @Order(4) void remittanceCreationIsReplaySafeAndOwned(){
  var r=create("r1",10000);status(r,200);remit=UUID.fromString(r.text("id"));payoutReference=r.text("payoutReference");
  var replay=create("r1",10000);status(replay,200);assertThat(replay.text("id")).isEqualTo(remit.toString());
  status(create("r1",20000),409);
  status(call("GET",base(),token("bob","CUSTOMER"),null,null),403);
  status(call("POST","/api/v1/remittances",token("alice","CUSTOMER"),createBody(10000),null),400);
  status(call("POST","/api/v1/remittances",token("alice","CUSTOMER"),"{\"sourceUsdMinor\":10000,\"beneficiaryName\":\"Ada\",\"bankCode\":\"058\",\"accountNumber\":\"bad\"}","bad"),400);
 }
 @Test @Order(5) void fundingMustBeOperatorVerifiedAndMatchAmount(){
  status(post(local()+"/funding","alice","CUSTOMER","{\"fundingReference\":\"f1\",\"sourceUsdMinor\":10000}"),403);
  var q=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":10000}");status(q,200);quote=q.text("id");assertThat(q.text("destinationMinor")).isEqualTo("14850000");
  status(post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}"),422);
  status(post(local()+"/funding","treasury","TREASURY","{\"fundingReference\":\"f1\",\"sourceUsdMinor\":9999}"),422);
  String funding="{\"fundingReference\":\"f1\",\"sourceUsdMinor\":10000}";
  status(post(local()+"/funding","treasury","TREASURY",funding),200);
  status(post(local()+"/funding","treasury","TREASURY",funding),200);
  assertThat(db.queryForObject("select count(*) from ledger_journal where reference=?",Integer.class,remit+":fund")).isEqualTo(1);
 }
 @Test @Order(6) void rejectsExpiredAndForeignQuotesAndRollsBack(){
  db.update("update fx_quote set expires_at=? where id=?",Instant.now().minusSeconds(1),UUID.fromString(quote));
  var expired=post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}");status(expired,422);assertThat(expired.text("code")).isEqualTo("QUOTE_EXPIRED");
  var foreign=post("/api/v1/fx/quotes","bob","CUSTOMER","{\"sourceUsdMinor\":10000}");status(foreign,200);
  status(post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+foreign.text("id")+"\"}"),403);
  assertThat(db.queryForObject("select status from remittance where id=?",String.class,remit)).isEqualTo("FUNDED");
  var fresh=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":10000}");status(fresh,200);quote=fresh.text("id");
 }
 @Test @Order(7) void conversionReservesFloatAndUnknownOutcomeDoesNotRefund(){
  var r=post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}");status(r,200);assertThat(r.text("status")).isEqualTo("PAYOUT_PENDING");
  status(post(base()+"/convert","alice","CUSTOMER","{\"quoteId\":\""+quote+"\"}"),200);
  status(post(local()+"/outcome","treasury","TREASURY","{\"outcome\":\"UNKNOWN\"}"),200);
  var unknown=post(local()+"/process","treasury","TREASURY",null);status(unknown,200);assertThat(unknown.text("status")).isEqualTo("PAYOUT_PENDING");
  assertThat(db.queryForObject("select reserved_minor from settlement_float",Long.class)).isEqualTo(14850000L);
  assertThat(db.queryForObject("select cash_minor from settlement_float",Long.class)).isEqualTo(100000000L);
 }
 @Test @Order(8) void recoveryAndDuplicateSuccessCaptureOnlyOnce() throws Exception {
  status(post(local()+"/outcome","treasury","TREASURY","{\"outcome\":\"SUCCESS\"}"),200);
  try(var ex=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new ArrayList<java.util.concurrent.Future<Result>>();
   for(int i=0;i<6;i++)tasks.add(ex.submit(()->post(local()+"/process","treasury","TREASURY",null)));
   for(var f:tasks){var rr=f.get();status(rr,200);assertThat(rr.text("status")).isEqualTo("PAID_OUT");}
  }
  status(post("/api/v1/local/remittances/recover","treasury","TREASURY",null),200);
  var done=post(local()+"/process","treasury","TREASURY",null);status(done,200);assertThat(done.text("status")).isEqualTo("PAID_OUT");
  assertThat(db.queryForObject("select reserved_minor from settlement_float",Long.class)).isZero();
  assertThat(db.queryForObject("select cash_minor from settlement_float",Long.class)).isEqualTo(85150000L);
  assertThat(db.queryForObject("select count(*) from ledger_journal where reference=?",Integer.class,payoutReference+":paid")).isEqualTo(1);
 }
 @Test @Order(9) void failedPayoutPreservesNgnEntitlementAndCanRetry(){
  var r=create("r2",10000);status(r,200);String id=r.text("id"),b="/api/v1/remittances/"+id,l="/api/v1/local/remittances/"+id;
  status(post(l+"/funding","treasury","TREASURY","{\"fundingReference\":\"f2\",\"sourceUsdMinor\":10000}"),200);
  var q=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":10000}");status(q,200);
  status(post(b+"/convert","alice","CUSTOMER","{\"quoteId\":\""+q.text("id")+"\"}"),200);
  status(post(l+"/outcome","treasury","TREASURY","{\"outcome\":\"FAILED\"}"),200);
  var failed=post(l+"/process","treasury","TREASURY",null);status(failed,200);assertThat(failed.text("status")).isEqualTo("PAYOUT_FAILED");
  assertThat(db.queryForObject("select cash_minor from settlement_float",Long.class)).isEqualTo(85150000L);
  var retry=post(b+"/retry","alice","CUSTOMER",null);status(retry,200);assertThat(retry.text("attempt")).isEqualTo("2");
  var paid=post(l+"/process","treasury","TREASURY",null);status(paid,200);assertThat(paid.text("status")).isEqualTo("PAID_OUT");
 }
 @Test @Order(10) void insufficientFloatRollsBackQuoteAndJournals(){
  var r=create("too-large",1000000);status(r,200);String id=r.text("id");
  status(post("/api/v1/local/remittances/"+id+"/funding","treasury","TREASURY","{\"fundingReference\":\"f3\",\"sourceUsdMinor\":1000000}"),200);
  var q=post("/api/v1/fx/quotes","alice","CUSTOMER","{\"sourceUsdMinor\":1000000}");status(q,200);
  var bad=post("/api/v1/remittances/"+id+"/convert","alice","CUSTOMER","{\"quoteId\":\""+q.text("id")+"\"}");status(bad,422);assertThat(bad.text("code")).isEqualTo("INSUFFICIENT_FLOAT");
  assertThat(db.queryForObject("select status from fx_quote where id=?",String.class,UUID.fromString(q.text("id")))).isEqualTo("OPEN");
  assertThat(db.queryForObject("select status from remittance where id=?",String.class,UUID.fromString(id))).isEqualTo("FUNDED");
  assertThat(db.queryForObject("select count(*) from ledger_journal where reference=?",Integer.class,id+":fx-usd")).isZero();
 }
 @Test @Order(11) void apiDocsExposeOAuthAndNewEndpoints(){
  var r=call("GET","/v3/api-docs",null,null,null);status(r,200);
  assertThat(r.body()).contains("/api/v1/settlements/float","/api/v1/fx/quotes","/api/v1/remittances","authorizationCode","PayBridge");
 }
 @Test @Order(12) void concurrentDuplicateCreateProducesOneOperation() throws Exception {
  try(var ex=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
   var tasks=new java.util.ArrayList<java.util.concurrent.Future<Result>>();
   for(int i=0;i<6;i++)tasks.add(ex.submit(()->create("parallel-1",10000)));
   var ids=new HashSet<String>();
   for(var f:tasks){var r=f.get();status(r,200);ids.add(r.text("id"));}
   assertThat(ids).hasSize(1);
  }
 }
 @Test @Order(13) void everyJournalBalancesPerCurrency(){
  var bad=db.queryForList("select journal_id,currency,sum(case when direction='DEBIT' then amount_minor else -amount_minor end) net from ledger_entry group by journal_id,currency having sum(case when direction='DEBIT' then amount_minor else -amount_minor end)<>0");
  assertThat(bad).isEmpty();
 }
}
```

### src/test/resources/application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:paybridge;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password: ''
    driver-class-name: org.h2.Driver
  flyway:
    enabled: false
  sql:
    init:
      mode: always
      schema-locations: classpath:http-test-schema.sql
  jpa:
    hibernate:
      ddl-auto: validate
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://issuer.test
          audiences: paybridge-api
paybridge:
  fx:
    quote-ttl-seconds: 60
```

### compose.test.yml

```yaml
services:
  postgres-test:
    image: postgres:17
    environment:
      POSTGRES_DB: paybridge_http_test
      POSTGRES_USER: paybridge
      POSTGRES_PASSWORD: paybridge-test-only
    ports: ["127.0.0.1:15433:5432"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U paybridge -d paybridge_http_test"]
      interval: 2s
      retries: 15
```

### src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker

```text
mock-maker-subclass
```

The zip also contains the portable H2 test schema. When PAYBRIDGE_TEST_DB_URL is set, tests use the real PostgreSQL Flyway migrations instead.
