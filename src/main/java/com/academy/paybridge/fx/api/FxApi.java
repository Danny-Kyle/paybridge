package com.academy.paybridge.fx.api;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
public interface FxApi {
 record Quote(UUID id,UUID customerId,long sourceMinor,long sourceFeeMinor,long destinationMinor,BigDecimal rate,Instant expiresAt,String status){}
 Quote accept(UUID quoteId,UUID operationId,UUID customerId,long sourceMinor);
}
