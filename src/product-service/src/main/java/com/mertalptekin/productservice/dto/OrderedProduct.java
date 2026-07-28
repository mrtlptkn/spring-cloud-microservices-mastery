package com.mertalptekin.productservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Not: İki farklı microservices request bazlı birbirleri ile haberleşirken
// Integration Patterns kısmında Customer-Supplier Pattern tercih edildi.
// Customer -> OrderService, Senaryo -> OrderedProductRequest Müşteri talep eden
// Supplier -> Service -> OrderedProductDetailResponse Tedarikçi talebe cevap veren.
// Bu yöntemde talebe cevap veren serviste request ve response değişikliği olursa orderService aynı
// şekilde kendini güncellemeli.


@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderedProduct {

    private String productId;
    private BigDecimal price;
    private Integer stock;

}