package com.mertalptekin.sagaservice.event;

public record RejectOrderEvent(String orderId,String reason)  { }
