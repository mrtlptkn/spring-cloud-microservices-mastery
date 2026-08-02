package com.mertalptekin.productservice.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record OrderedProductDetailRequest(
		@JsonProperty("ProductIds")
		@JsonAlias("productIds")
		String[] productIds
) {
}
