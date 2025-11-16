package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.model.Product;
import com.example.tricol.tricolspringbootrestapi.model.StockSlot;
import com.example.tricol.tricolspringbootrestapi.repository.ProductRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockValuationServiceImplTest {

    @Mock
    private StockSlotRepository stockSlotRepository;
    
    @Mock
    private ProductRepository productRepository;

    private double calculateStockValuation(Product product, List<StockSlot> slots) {
        double totalValue = 0.0;
        
        for (StockSlot slot : slots) {
            if (slot.getAvailableQuantity() > 0) {
                totalValue += slot.getAvailableQuantity() * slot.getUnitPrice();
            }
        }
        
        return totalValue;
    }

    private double calculateTotalStockValuation(List<Product> products) {
        double totalValue = 0.0;
        
        for (Product product : products) {
            List<StockSlot> slots = stockSlotRepository.findByProduct(product);
            totalValue += calculateStockValuation(product, slots);
        }
        
        return totalValue;
    }

    @Test
    void calculateStockValuation_singleLot_returnsCorrectValue() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Widget");
        product.setCurrentStock(100.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(product);
        slot.setQuantity(100.0);
        slot.setAvailableQuantity(100.0);
        slot.setUnitPrice(15.50);
        slot.setEntryDate(LocalDateTime.now());

        double valuation = calculateStockValuation(product, List.of(slot));

        assertEquals(1550.00, valuation, 0.01, 
            "Valuation should equal available quantity multiplied by unit price");

        verify(stockSlotRepository, never()).save(any());
    }

    @Test
    void calculateStockValuation_multipleLotsDifferentPrices_usesFIFO() {
        Product product = new Product();
        product.setId(2L);
        product.setName("Gadget");
        product.setCurrentStock(150.0);

        StockSlot slotOld = new StockSlot();
        slotOld.setId(10L);
        slotOld.setProduct(product);
        slotOld.setQuantity(50.0);
        slotOld.setAvailableQuantity(50.0);
        slotOld.setUnitPrice(10.00);
        slotOld.setEntryDate(LocalDateTime.now().minusDays(30));

        StockSlot slotMid = new StockSlot();
        slotMid.setId(11L);
        slotMid.setProduct(product);
        slotMid.setQuantity(60.0);
        slotMid.setAvailableQuantity(60.0);
        slotMid.setUnitPrice(12.50);
        slotMid.setEntryDate(LocalDateTime.now().minusDays(15));

        StockSlot slotNew = new StockSlot();
        slotNew.setId(12L);
        slotNew.setProduct(product);
        slotNew.setQuantity(40.0);
        slotNew.setAvailableQuantity(40.0);
        slotNew.setUnitPrice(15.00);
        slotNew.setEntryDate(LocalDateTime.now().minusDays(5));

        List<StockSlot> slots = Arrays.asList(slotOld, slotMid, slotNew);

        double valuation = calculateStockValuation(product, slots);

        assertEquals(1850.00, valuation, 0.01,
            "Valuation should sum all lots according to FIFO");
    }

    @Test
    void calculateStockValuation_partiallyConsumedLots_calculatesRemainingValue() {
        Product product = new Product();
        product.setId(3L);
        product.setName("Component");
        product.setCurrentStock(85.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(20L);
        slot1.setProduct(product);
        slot1.setQuantity(100.0);
        slot1.setAvailableQuantity(30.0);
        slot1.setUnitPrice(8.00);
        slot1.setEntryDate(LocalDateTime.now().minusDays(20));

        StockSlot slot2 = new StockSlot();
        slot2.setId(21L);
        slot2.setProduct(product);
        slot2.setQuantity(80.0);
        slot2.setAvailableQuantity(55.0);
        slot2.setUnitPrice(9.50);
        slot2.setEntryDate(LocalDateTime.now().minusDays(19));

        List<StockSlot> slots = Arrays.asList(slot1, slot2);

        double valuation = calculateStockValuation(product, slots);

        assertEquals(762.50, valuation, 0.01,
            "Valuation should use only remaining available quantities");
    }

    @Test
    void calculateStockValuation_fullyConsumedLots_excludesFromValuation() {
        Product product = new Product();
        product.setId(4L);
        product.setName("Material");
        product.setCurrentStock(50.0);

        StockSlot slotEmpty1 = new StockSlot();
        slotEmpty1.setId(30L);
        slotEmpty1.setProduct(product);
        slotEmpty1.setQuantity(100.0);
        slotEmpty1.setAvailableQuantity(0.0);
        slotEmpty1.setUnitPrice(10.00);
        slotEmpty1.setEntryDate(LocalDateTime.now().minusDays(50));

        StockSlot slotEmpty2 = new StockSlot();
        slotEmpty2.setId(31L);
        slotEmpty2.setProduct(product);
        slotEmpty2.setQuantity(75.0);
        slotEmpty2.setAvailableQuantity(0.0);
        slotEmpty2.setUnitPrice(11.00);
        slotEmpty2.setEntryDate(LocalDateTime.now().minusDays(40));

        StockSlot slotActive = new StockSlot();
        slotActive.setId(32L);
        slotActive.setProduct(product);
        slotActive.setQuantity(50.0);
        slotActive.setAvailableQuantity(50.0);
        slotActive.setUnitPrice(12.00);
        slotActive.setEntryDate(LocalDateTime.now().minusDays(10));

        List<StockSlot> slots = Arrays.asList(slotEmpty1, slotEmpty2, slotActive);

        double valuation = calculateStockValuation(product, slots);

        assertEquals(600.00, valuation, 0.01,
            "Fully consumed lots should not contribute to valuation");
    }

    @Test
    void calculateStockValuation_noStockSlots_returnsZero() {
        Product product = new Product();
        product.setId(5L);
        product.setName("Empty Product");
        product.setCurrentStock(0.0);

        double valuation = calculateStockValuation(product, Collections.emptyList());

        assertEquals(0.0, valuation, 0.01,
            "Product with no stock slots should have zero valuation");
    }

    @Test
    void calculateStockValuation_multipleLotsSamePrice_aggregatesCorrectly() {
        Product product = new Product();
        product.setId(6L);
        product.setName("Bulk Item");
        product.setCurrentStock(200.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(40L);
        slot1.setProduct(product);
        slot1.setQuantity(80.0);
        slot1.setAvailableQuantity(80.0);
        slot1.setUnitPrice(20.00);
        slot1.setEntryDate(LocalDateTime.now().minusDays(25));

        StockSlot slot2 = new StockSlot();
        slot2.setId(41L);
        slot2.setProduct(product);
        slot2.setQuantity(70.0);
        slot2.setAvailableQuantity(70.0);
        slot2.setUnitPrice(20.00);
        slot2.setEntryDate(LocalDateTime.now().minusDays(15));

        StockSlot slot3 = new StockSlot();
        slot3.setId(42L);
        slot3.setProduct(product);
        slot3.setQuantity(50.0);
        slot3.setAvailableQuantity(50.0);
        slot3.setUnitPrice(20.00);
        slot3.setEntryDate(LocalDateTime.now().minusDays(5));

        List<StockSlot> slots = Arrays.asList(slot1, slot2, slot3);

        double valuation = calculateStockValuation(product, slots);

        assertEquals(4000.00, valuation, 0.01,
            "Lots with same price should be correctly aggregated");
    }

    @Test
    void calculateStockValuation_highPriceVariation_reflectsFIFOAccurately() {
        Product product = new Product();
        product.setId(7L);
        product.setName("Volatile Price Item");
        product.setCurrentStock(90.0);

        StockSlot slotCheap = new StockSlot();
        slotCheap.setId(50L);
        slotCheap.setProduct(product);
        slotCheap.setQuantity(30.0);
        slotCheap.setAvailableQuantity(30.0);
        slotCheap.setUnitPrice(5.00);
        slotCheap.setEntryDate(LocalDateTime.now().minusMonths(6));

        StockSlot slotExpensive = new StockSlot();
        slotExpensive.setId(51L);
        slotExpensive.setProduct(product);
        slotExpensive.setQuantity(60.0);
        slotExpensive.setAvailableQuantity(60.0);
        slotExpensive.setUnitPrice(25.00);
        slotExpensive.setEntryDate(LocalDateTime.now().minusDays(3));

        List<StockSlot> slots = Arrays.asList(slotCheap, slotExpensive);

        double valuation = calculateStockValuation(product, slots);

        assertEquals(1650.00, valuation, 0.01,
            "FIFO valuation should reflect price variations between old and new lots");

        double expectedAveragePrice = 1650.00 / 90.0;
        assertEquals(18.33, expectedAveragePrice, 0.01,
            "Weighted average price should be calculated correctly");
    }

    @Test
    void calculateStockValuation_decimalQuantities_handlesCorrectly() {
        Product product = new Product();
        product.setId(8L);
        product.setName("Liquid Material");
        product.setCurrentStock(125.75);

        StockSlot slot1 = new StockSlot();
        slot1.setId(60L);
        slot1.setProduct(product);
        slot1.setQuantity(45.25);
        slot1.setAvailableQuantity(45.25);
        slot1.setUnitPrice(7.80);
        slot1.setEntryDate(LocalDateTime.now().minusDays(20));

        StockSlot slot2 = new StockSlot();
        slot2.setId(61L);
        slot2.setProduct(product);
        slot2.setQuantity(80.50);
        slot2.setAvailableQuantity(80.50);
        slot2.setUnitPrice(8.35);
        slot2.setEntryDate(LocalDateTime.now().minusDays(10));

        List<StockSlot> slots = Arrays.asList(slot1, slot2);

        double valuation = calculateStockValuation(product, slots);

        assertEquals(1025.13, valuation, 0.01,
            "Valuation should handle decimal quantities and prices correctly");
    }

    @Test
    void calculateTotalStockValuation_multipleProducts_aggregatesCorrectly() {
        Product product1 = new Product();
        product1.setId(10L);
        product1.setName("Product A");

        Product product2 = new Product();
        product2.setId(11L);
        product2.setName("Product B");

        StockSlot slot1A = new StockSlot();
        slot1A.setProduct(product1);
        slot1A.setAvailableQuantity(50.0);
        slot1A.setUnitPrice(10.00);

        StockSlot slot2A = new StockSlot();
        slot2A.setProduct(product1);
        slot2A.setAvailableQuantity(30.0);
        slot2A.setUnitPrice(12.00);

        StockSlot slot1B = new StockSlot();
        slot1B.setProduct(product2);
        slot1B.setAvailableQuantity(40.0);
        slot1B.setUnitPrice(15.00);

        when(stockSlotRepository.findByProduct(product1))
            .thenReturn(Arrays.asList(slot1A, slot2A));
        when(stockSlotRepository.findByProduct(product2))
            .thenReturn(List.of(slot1B));

        List<Product> products = Arrays.asList(product1, product2);

        double totalValuation = calculateTotalStockValuation(products);

        assertEquals(1460.00, totalValuation, 0.01,
            "Total valuation should correctly aggregate all products");
    }
}
