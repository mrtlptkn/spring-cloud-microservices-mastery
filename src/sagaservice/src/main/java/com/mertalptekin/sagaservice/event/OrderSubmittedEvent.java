package com.mertalptekin.sagaservice.event;

public record OrderSubmittedEvent(String orderId,String code, Integer quantity) { }
