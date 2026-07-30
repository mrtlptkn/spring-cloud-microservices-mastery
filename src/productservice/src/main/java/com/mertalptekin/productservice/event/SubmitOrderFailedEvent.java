package com.mertalptekin.productservice.event;

// Şu orderCode reason sebebi ile geri alma eventi
// compensate -> geri alma işlemleri
public record SubmitOrderFailedEvent(String orderCode,String reason) {
}
