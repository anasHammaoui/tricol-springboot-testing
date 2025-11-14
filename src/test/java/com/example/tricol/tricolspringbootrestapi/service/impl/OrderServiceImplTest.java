package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.dto.response.ReceiveOrderResponse;
import com.example.tricol.tricolspringbootrestapi.exception.InvalidOperationException;
import com.example.tricol.tricolspringbootrestapi.exception.ResourceNotFoundException;
import com.example.tricol.tricolspringbootrestapi.mapper.OrderMapper;
import com.example.tricol.tricolspringbootrestapi.model.*;
import com.example.tricol.tricolspringbootrestapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockSlotRepository stockSlotRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(i -> {
            StockSlot slot = i.getArgument(0);
            if (slot.getEntryDate() == null) {
                slot.setEntryDate(LocalDateTime.now());
            }
            return slot;
        });
        lenient().when(stockSlotRepository.saveAll(anyList())).thenAnswer(i -> {
            List<StockSlot> slots = i.getArgument(0);
            slots.forEach(slot -> {
                if (slot.getEntryDate() == null) {
                    slot.setEntryDate(LocalDateTime.now());
                }
            });
            return slots;
        });
        lenient().when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(orderMapper.toReceiveOrderResponse(any(Order.class))).thenReturn(new ReceiveOrderResponse());
    }

    @Test
    void receiveOrder_createsStockSlotAutomatically_withTraceability() {
        // ARRANGE: Setup supplier order with items
        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setSociety("Fournisseur Test");

        Product product1 = new Product();
        product1.setId(100L);
        product1.setName("Product A");
        product1.setCurrentStock(50.0);
        product1.setUnitPrice(10.50);

        Product product2 = new Product();
        product2.setId(200L);
        product2.setName("Product B");
        product2.setCurrentStock(30.0);
        product2.setUnitPrice(25.75);

        OrderItem item1 = new OrderItem();
        item1.setId(1L);
        item1.setProduct(product1);
        item1.setQuantity(100.0);
        item1.setUnitPrice(10.50);
        item1.setTotal(1050.0);

        OrderItem item2 = new OrderItem();
        item2.setId(2L);
        item2.setProduct(product2);
        item2.setQuantity(50.0);
        item2.setUnitPrice(25.75);
        item2.setTotal(1287.5);

        Order order = new Order();
        order.setId(1000L);
        order.setSupplier(supplier);
        order.setStatus(Order.OrderStatus.pending);
        order.setOrderDate(LocalDateTime.now());
        order.setTotalAmount(2337.5);
        order.setItems(new ArrayList<>(List.of(item1, item2)));

        item1.setOrder(order);
        item2.setOrder(order);

        when(orderRepository.findById(1000L)).thenReturn(Optional.of(order));

        // ACT: Receive the order (should create stock slots automatically)
        LocalDateTime beforeReceive = LocalDateTime.now().minusSeconds(1);
        ReceiveOrderResponse response = orderService.receiveOrder(1000L);
        LocalDateTime afterReceive = LocalDateTime.now().plusSeconds(1);

        // ASSERT 1: Order status changed to delivered
        assertEquals(Order.OrderStatus.delivered, order.getStatus());

        // ASSERT 2: Stock slots were created (one per order item)
        ArgumentCaptor<List<StockSlot>> stockSlotListCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockSlotRepository, times(1)).saveAll(stockSlotListCaptor.capture());

        List<StockSlot> createdSlots = stockSlotListCaptor.getValue();
        assertEquals(2, createdSlots.size(), "Should create 2 stock slots for 2 order items");

        // ASSERT 3: Verify first stock slot (lot) details
        StockSlot slot1 = createdSlots.get(0);
        assertNotNull(slot1, "Stock slot 1 should be created");
        assertEquals(order, slot1.getOrder(), "Stock slot should be linked to the order");
        assertEquals(product1, slot1.getProduct(), "Stock slot should be linked to product 1");
        assertEquals(100.0, slot1.getQuantity(), "Stock slot quantity should match order item quantity");
        assertEquals(100.0, slot1.getAvailableQuantity(), "Initial available quantity should equal total quantity");
        assertEquals(10.50, slot1.getUnitPrice(), "Stock slot should record unit purchase price from order item");
        assertNotNull(slot1.getEntryDate(), "Entry date should be auto-generated");
        assertTrue(slot1.getEntryDate().isAfter(beforeReceive) && slot1.getEntryDate().isBefore(afterReceive),
                "Entry date should be set at creation time");

        // ASSERT 4: Verify second stock slot (lot) details
        StockSlot slot2 = createdSlots.get(1);
        assertNotNull(slot2, "Stock slot 2 should be created");
        assertEquals(order, slot2.getOrder(), "Stock slot should be linked to the order");
        assertEquals(product2, slot2.getProduct(), "Stock slot should be linked to product 2");
        assertEquals(50.0, slot2.getQuantity(), "Stock slot quantity should match order item quantity");
        assertEquals(50.0, slot2.getAvailableQuantity(), "Initial available quantity should equal total quantity");
        assertEquals(25.75, slot2.getUnitPrice(), "Stock slot should record unit purchase price from order item");
        assertNotNull(slot2.getEntryDate(), "Entry date should be auto-generated");

        // ASSERT 5: Verify product stock updated
        assertEquals(150.0, product1.getCurrentStock(), "Product 1 stock should increase by 100");
        assertEquals(80.0, product2.getCurrentStock(), "Product 2 stock should increase by 50");
        verify(productRepository, times(2)).save(any(Product.class));

        // ASSERT 6: Verify stock movements created (IN movements)
        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movementCaptor.capture());

        List<StockMovement> movements = movementCaptor.getAllValues();

        StockMovement movement1 = movements.get(0);
        assertEquals(StockMovement.Type.in, movement1.getType(), "Movement should be IN type");
        assertEquals(100.0, movement1.getQuantity(), "Movement quantity should match slot quantity");
        assertEquals(product1, movement1.getProduct(), "Movement should reference product 1");
        assertEquals(slot1, movement1.getStockSlot(), "Movement should be linked to stock slot 1");
        assertEquals(order, movement1.getOrder(), "Movement should be linked to the order");

        StockMovement movement2 = movements.get(1);
        assertEquals(StockMovement.Type.in, movement2.getType(), "Movement should be IN type");
        assertEquals(50.0, movement2.getQuantity(), "Movement quantity should match slot quantity");
        assertEquals(product2, movement2.getProduct(), "Movement should reference product 2");
        assertEquals(slot2, movement2.getStockSlot(), "Movement should be linked to stock slot 2");
        assertEquals(order, movement2.getOrder(), "Movement should be linked to the order");

        // ASSERT 7: Verify order saved with stock slots linked
        verify(orderRepository, times(1)).save(order);
        assertEquals(2, order.getStockSlot().size(), "Order should have 2 stock slots linked");
    }

    @Test
    void receiveOrder_alreadyDelivered_throwsException() {
        // ARRANGE: Order already delivered
        Order order = new Order();
        order.setId(2000L);
        order.setStatus(Order.OrderStatus.delivered);

        when(orderRepository.findById(2000L)).thenReturn(Optional.of(order));

        // ACT & ASSERT: Should throw exception
        InvalidOperationException exception = assertThrows(InvalidOperationException.class, () -> {
            orderService.receiveOrder(2000L);
        });

        assertEquals("Order has already been received", exception.getMessage());

        // Verify no stock slots created
        verify(stockSlotRepository, never()).saveAll(anyList());
        verify(stockMovementRepository, never()).save(any(StockMovement.class));
    }

    @Test
    void receiveOrder_orderNotFound_throwsException() {
        // ARRANGE: Order doesn't exist
        when(orderRepository.findById(9999L)).thenReturn(Optional.empty());

        // ACT & ASSERT: Should throw exception
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            orderService.receiveOrder(9999L);
        });

        assertEquals("Order with id 9999 not found", exception.getMessage());

        // Verify no stock slots created
        verify(stockSlotRepository, never()).saveAll(anyList());
    }

    @Test
    void receiveOrder_multipleProductsSameOrder_createsMultipleTraceableSlots() {
        // ARRANGE: Order with 3 different products
        Supplier supplier = new Supplier();
        supplier.setId(1L);

        Product prod1 = new Product();
        prod1.setId(1L);
        prod1.setName("Prod1");
        prod1.setCurrentStock(0.0);

        Product prod2 = new Product();
        prod2.setId(2L);
        prod2.setName("Prod2");
        prod2.setCurrentStock(0.0);

        Product prod3 = new Product();
        prod3.setId(3L);
        prod3.setName("Prod3");
        prod3.setCurrentStock(0.0);

        OrderItem item1 = new OrderItem();
        item1.setProduct(prod1);
        item1.setQuantity(20.0);
        item1.setUnitPrice(5.0);

        OrderItem item2 = new OrderItem();
        item2.setProduct(prod2);
        item2.setQuantity(30.0);
        item2.setUnitPrice(7.5);

        OrderItem item3 = new OrderItem();
        item3.setProduct(prod3);
        item3.setQuantity(40.0);
        item3.setUnitPrice(12.0);

        Order order = new Order();
        order.setId(3000L);
        order.setSupplier(supplier);
        order.setStatus(Order.OrderStatus.pending);
        order.setItems(new ArrayList<>(List.of(item1, item2, item3)));

        item1.setOrder(order);
        item2.setOrder(order);
        item3.setOrder(order);

        when(orderRepository.findById(3000L)).thenReturn(Optional.of(order));

        // ACT
        orderService.receiveOrder(3000L);

        // ASSERT: 3 stock slots created with proper traceability
        ArgumentCaptor<List<StockSlot>> captor = ArgumentCaptor.forClass(List.class);
        verify(stockSlotRepository, times(1)).saveAll(captor.capture());

        List<StockSlot> slots = captor.getValue();
        assertEquals(3, slots.size(), "Should create 3 traceable stock slots");

        // Verify each slot is properly linked and traced
        for (int i = 0; i < 3; i++) {
            StockSlot slot = slots.get(i);
            assertNotNull(slot.getOrder(), "Slot should be linked to order for traceability");
            assertNotNull(slot.getProduct(), "Slot should be linked to product");
            assertNotNull(slot.getEntryDate(), "Slot should have entry date for traceability");
            assertNotNull(slot.getUnitPrice(), "Slot should record unit purchase price");
            assertEquals(slot.getQuantity(), slot.getAvailableQuantity(), "Initial available = total quantity");
        }

        // Verify stock movements created for traceability
        verify(stockMovementRepository, times(3)).save(any(StockMovement.class));
    }
}
