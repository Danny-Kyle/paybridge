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
