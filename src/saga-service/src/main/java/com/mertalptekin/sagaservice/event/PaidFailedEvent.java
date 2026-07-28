package com.mertalptekin.sagaservice.event;

public record PaidFailedEvent(String orderId,String code,String message) { }
