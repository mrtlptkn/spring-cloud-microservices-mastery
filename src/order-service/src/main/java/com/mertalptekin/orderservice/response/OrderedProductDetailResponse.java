package com.mertalptekin.orderservice.response;
import com.mertalptekin.orderservice.dto.OrderedProduct;
import java.util.List;

public record OrderedProductDetailResponse(List<OrderedProduct> orderedProducts) {
}
