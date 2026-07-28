package com.mertalptekin.sagaservice.repository;

import com.mertalptekin.sagaservice.entity.OrderSagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IOrderSagaRepository extends JpaRepository<OrderSagaState,String> {
}
