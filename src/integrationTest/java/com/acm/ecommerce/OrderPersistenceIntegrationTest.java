package com.acm.ecommerce;

import com.acm.ecommerce.dto.OrderDTO;
import com.acm.ecommerce.entities.ProductEntity;
import com.acm.ecommerce.repository.OrderRepository;
import com.acm.ecommerce.repository.ProductRepository;
import com.acm.ecommerce.services.OrderService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderPersistenceIntegrationTest {
    @Autowired
    private ProductRepository products;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderService orderService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsOrderItemsTotalAndStockWithRealDatabase() {
        ProductEntity product = new ProductEntity();
        product.setName("Keyboard");
        product.setPrice(25.0);
        product.setStock(10);
        products.saveAndFlush(product);

        OrderDTO.OrderResponse response = orderService.createOrder(new OrderDTO.OrderRequest(42L,
                List.of(new OrderDTO.OrderItemRequest(product.getId(), 3))));
        entityManager.flush();
        entityManager.clear();

        var saved = orders.findById(response.id()).orElseThrow();
        assertThat(saved.getTotalAmount()).isEqualTo(75.0);
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().getFirst().getProduct().getId()).isEqualTo(product.getId());
        assertThat(saved.getItems().getFirst().getQuantity()).isEqualTo(3);
        assertThat(products.findById(product.getId()).orElseThrow().getStock()).isEqualTo(7);
        assertThat(orders.findByUserId(42L)).hasSize(1);
    }

    @Test
    void softDeleteHidesProductButPreservesDatabaseRow() {
        ProductEntity product = new ProductEntity();
        product.setName("Mouse");
        product.setPrice(15.0);
        product.setStock(4);
        products.saveAndFlush(product);

        products.deleteById(product.getId());
        products.flush();
        entityManager.clear();

        assertThat(products.findById(product.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select deleted from products where id = ?",
                Boolean.class, product.getId())).isTrue();
    }
}
