package com.mertalptekin.sagaservice.event;

public record StockNotAvailableEvent(String orderId,String code) { }
