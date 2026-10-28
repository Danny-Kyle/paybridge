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
