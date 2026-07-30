package com.mertalptekin.productservice.response;

import com.mertalptekin.productservice.dto.OrderedProduct;

import java.util.List;

public record OrderedProductDetailResponse(List<OrderedProduct> orderedProducts) {
}
