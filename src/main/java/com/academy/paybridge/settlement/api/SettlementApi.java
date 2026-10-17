package com.academy.paybridge.settlement.api;
import java.util.UUID;
public interface SettlementApi {
 void reserve(UUID operationId,long ngnMinor);
 void capture(UUID operationId);
 void release(UUID operationId);
}
