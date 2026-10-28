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
