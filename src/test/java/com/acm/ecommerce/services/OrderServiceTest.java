package com.acm.ecommerce.services;

import com.acm.ecommerce.dto.OrderDTO;
import com.acm.ecommerce.entities.OrderEntity;
import com.acm.ecommerce.entities.ProductEntity;
import com.acm.ecommerce.mapper.OrderMapper;
import com.acm.ecommerce.repository.OrderRepository;
import com.acm.ecommerce.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private OrderMapper orderMapper;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, productRepository, orderMapper);
    }

    @Test
    void createsOrderAndCalculatesTotalAcrossProducts() {
        ProductEntity first = product(1L, 10.0, 5);
        ProductEntity second = product(2L, 7.5, 4);
        when(productRepository.findById(1L)).thenReturn(Optional.of(first));
        when(productRepository.findById(2L)).thenReturn(Optional.of(second));
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(call -> call.getArgument(0));
        OrderDTO.OrderResponse response = new OrderDTO.OrderResponse(10L, 20L, null, 42.5, List.of());
        when(orderMapper.toResponse(any(OrderEntity.class))).thenReturn(response);

        OrderDTO.OrderResponse actual = service.createOrder(new OrderDTO.OrderRequest(20L, List.of(
                new OrderDTO.OrderItemRequest(1L, 2),
                new OrderDTO.OrderItemRequest(2L, 3))));

        assertThat(actual).isEqualTo(response);
        assertThat(first.getStock()).isEqualTo(3);
        assertThat(second.getStock()).isEqualTo(1);
        ArgumentCaptor<OrderEntity> saved = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getTotalAmount()).isEqualTo(42.5);
        assertThat(saved.getValue().getUserId()).isEqualTo(20L);
        assertThat(saved.getValue().getItems()).hasSize(2);
        assertThat(saved.getValue().getItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(saved.getValue().getItems().getFirst().getPrice()).isEqualTo(10.0);
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() {
        ProductEntity product = product(1L, 10.0, 1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.createOrder(new OrderDTO.OrderRequest(20L,
                List.of(new OrderDTO.OrderItemRequest(1L, 2)))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Insufficient stock");

        assertThat(product.getStock()).isEqualTo(1);
        verify(productRepository, never()).save(any(ProductEntity.class));
        verify(orderRepository, never()).save(any(OrderEntity.class));
    }

    @Test
    void rejectsOrderWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrder(new OrderDTO.OrderRequest(20L,
                List.of(new OrderDTO.OrderItemRequest(99L, 1)))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Product not found: 99");

        verify(orderRepository, never()).save(any(OrderEntity.class));
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    private ProductEntity product(long id, double price, int stock) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setName("Product " + id);
        product.setPrice(price);
        product.setStock(stock);
        return product;
    }
}
