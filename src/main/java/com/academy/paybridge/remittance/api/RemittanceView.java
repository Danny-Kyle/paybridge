package com.academy.paybridge.remittance.api;
import java.util.UUID;
public record RemittanceView(UUID id,UUID customerId,long sourceUsdMinor,long destinationNgnMinor,String status,String payoutReference,int attempt){}
