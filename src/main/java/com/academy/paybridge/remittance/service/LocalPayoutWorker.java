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
