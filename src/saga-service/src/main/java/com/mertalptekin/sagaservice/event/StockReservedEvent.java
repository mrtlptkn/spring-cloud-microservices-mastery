package com.mertalptekin.sagaservice.event;

public record StockReservedEvent(String orderId,String code,Integer quantity) { }
