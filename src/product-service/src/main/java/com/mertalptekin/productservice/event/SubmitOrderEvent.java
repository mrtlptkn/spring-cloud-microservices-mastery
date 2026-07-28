package com.mertalptekin.productservice.event;

import com.mertalptekin.productservice.dto.OrderedProduct;

import java.util.List;


public record SubmitOrderEvent(String orderCode, List<OrderedProduct> items) {
}