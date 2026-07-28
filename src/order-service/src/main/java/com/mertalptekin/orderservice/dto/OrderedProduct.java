package com.mertalptekin.orderservice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderedProduct {

    private String productId;
    private BigDecimal price;
    private Integer stock;

}
