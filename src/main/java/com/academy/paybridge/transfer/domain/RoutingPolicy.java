package com.academy.paybridge.transfer.domain;

public interface RoutingPolicy { RoutingDecision decide(TransferSnapshot snapshot); }