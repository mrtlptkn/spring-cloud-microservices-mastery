package com.mertalptekin.orderservice.event;

public record SubmitOrderFailedEvent(String orderCode,String reason) {
}
