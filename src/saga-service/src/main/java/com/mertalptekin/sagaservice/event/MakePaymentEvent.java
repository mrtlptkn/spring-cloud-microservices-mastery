package com.mertalptekin.sagaservice.event;

public record MakePaymentEvent(String orderId,String code,Double amount) {
}
