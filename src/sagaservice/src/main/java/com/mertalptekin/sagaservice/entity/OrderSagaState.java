package com.mertalptekin.sagaservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Table(name = "order-saga-states")
@Entity
public class OrderSagaState {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;


    // Order'a ait tüm stateler için AggregationId yada CorrelationId
    @Column
    private String orderId;

    @Column
    private String status;

    @Column
    private String reason;

    @Column
    private LocalDateTime createdAt;


}
