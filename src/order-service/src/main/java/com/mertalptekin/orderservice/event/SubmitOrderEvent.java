package com.mertalptekin.orderservice.event;

import com.mertalptekin.orderservice.dto.OrderedProduct;

import java.util.List;

// orderCode -> üzerinden itemları sipariş ettik.
// orderCode -> burada operasyonun takibini sağlamak için kullanılanan unique operasyon idsi
// bunun yerine correlationId de kullanılabilir.
public record SubmitOrderEvent(String orderCode, List<OrderedProduct> items) {
}
