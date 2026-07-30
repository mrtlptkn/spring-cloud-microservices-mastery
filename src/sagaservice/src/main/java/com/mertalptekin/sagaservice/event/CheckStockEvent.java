package com.mertalptekin.sagaservice.event;

public record CheckStockEvent(String orderId, String code, Integer quantity) { }
