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
