package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.enums.ExitSlipStatus;
import com.example.tricol.tricolspringbootrestapi.model.ExitSlip;
import com.example.tricol.tricolspringbootrestapi.model.ExitSlipItem;
import com.example.tricol.tricolspringbootrestapi.model.Product;
import com.example.tricol.tricolspringbootrestapi.model.StockSlot;
import com.example.tricol.tricolspringbootrestapi.model.StockMovement;
import com.example.tricol.tricolspringbootrestapi.repository.ExitSlipRepository;
import com.example.tricol.tricolspringbootrestapi.repository.ProductRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockMovementRepository;
import com.example.tricol.tricolspringbootrestapi.repository.StockSlotRepository;
import com.example.tricol.tricolspringbootrestapi.mapper.ExitSlipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExitSlipStatusTransitionTest {

    @Mock
    private ExitSlipRepository exitSlipRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private StockSlotRepository stockSlotRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;
    @Mock
    private ExitSlipMapper exitSlipMapper;

    @InjectMocks
    private ExitSlipServiceImpl exitSlipService;

    @BeforeEach
    void setUp() {
        lenient().when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void validateExitSlip_statusTransitionFromDraftToValidated_updatesStatus() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setCurrentStock(100.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(product);
        slot.setAvailableQuantity(100.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(10));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(20));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));

        exitSlipService.validateExitSlip(1L);

        assertEquals(ExitSlipStatus.VALIDATED, slip.getStatus());
        verify(exitSlipRepository, times(1)).save(slip);
    }

    @Test
    void validateExitSlip_createsStockMovementWithCorrectType() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setCurrentStock(100.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(product);
        slot.setAvailableQuantity(100.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(10));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(25));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));

        exitSlipService.validateExitSlip(1L);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(movementCaptor.capture());
        
        StockMovement savedMovement = movementCaptor.getValue();
        assertEquals(StockMovement.Type.out, savedMovement.getType());
        assertEquals(-25.0, savedMovement.getQuantity());
        assertEquals(product, savedMovement.getProduct());
        assertEquals(slot, savedMovement.getStockSlot());
    }

    @Test
    void validateExitSlip_updatesRemainingQuantitiesInSlots() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setCurrentStock(100.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(1L);
        slot1.setProduct(product);
        slot1.setAvailableQuantity(30.0);
        slot1.setEntryDate(LocalDateTime.now().minusDays(20));

        StockSlot slot2 = new StockSlot();
        slot2.setId(2L);
        slot2.setProduct(product);
        slot2.setAvailableQuantity(70.0);
        slot2.setEntryDate(LocalDateTime.now().minusDays(10));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(50));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(Arrays.asList(slot1, slot2));

        exitSlipService.validateExitSlip(1L);

        assertEquals(0.0, slot1.getAvailableQuantity());
        assertEquals(50.0, slot2.getAvailableQuantity());
        
        verify(stockSlotRepository, times(1)).save(slot1);
        verify(stockSlotRepository, times(1)).save(slot2);
    }

    @Test
    void validateExitSlip_recordsValidationMetadata() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setCurrentStock(100.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(product);
        slot.setAvailableQuantity(100.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(10));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(15));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.setValidatedAt(null);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));

        LocalDateTime beforeValidation = LocalDateTime.now();
        exitSlipService.validateExitSlip(1L);
        LocalDateTime afterValidation = LocalDateTime.now();

        assertNotNull(slip.getValidatedAt());
        assertTrue(slip.getValidatedAt().isAfter(beforeValidation.minusSeconds(1)));
        assertTrue(slip.getValidatedAt().isBefore(afterValidation.plusSeconds(1)));
    }

    @Test
    void validateExitSlip_multipleItemsCreateMultipleMovements() {
        Product product1 = new Product();
        product1.setId(1L);
        product1.setName("Product 1");
        product1.setCurrentStock(100.0);

        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("Product 2");
        product2.setCurrentStock(50.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(1L);
        slot1.setProduct(product1);
        slot1.setAvailableQuantity(100.0);
        slot1.setEntryDate(LocalDateTime.now().minusDays(10));

        StockSlot slot2 = new StockSlot();
        slot2.setId(2L);
        slot2.setProduct(product2);
        slot2.setAvailableQuantity(50.0);
        slot2.setEntryDate(LocalDateTime.now().minusDays(5));

        ExitSlipItem item1 = new ExitSlipItem();
        item1.setId(1L);
        item1.setProduct(product1);
        item1.setRequestedQuantity(BigDecimal.valueOf(30));

        ExitSlipItem item2 = new ExitSlipItem();
        item2.setId(2L);
        item2.setProduct(product2);
        item2.setRequestedQuantity(BigDecimal.valueOf(20));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item1);
        slip.getItems().add(item2);
        item1.setExitSlip(slip);
        item2.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product1), eq(0.0)))
                .thenReturn(List.of(slot1));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product2), eq(0.0)))
                .thenReturn(List.of(slot2));

        exitSlipService.validateExitSlip(1L);

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
        
        List<StockMovement> movements = movementCaptor.getAllValues();
        assertEquals(2, movements.size());
        
        StockMovement movement1 = movements.get(0);
        assertEquals(product1, movement1.getProduct());
        assertEquals(-30.0, movement1.getQuantity());
        
        StockMovement movement2 = movements.get(1);
        assertEquals(product2, movement2.getProduct());
        assertEquals(-20.0, movement2.getQuantity());
    }

    @Test
    void validateExitSlip_updatesProductCurrentStock() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        product.setCurrentStock(100.0);

        StockSlot slot = new StockSlot();
        slot.setId(1L);
        slot.setProduct(product);
        slot.setAvailableQuantity(100.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(10));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(35));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));

        exitSlipService.validateExitSlip(1L);

        assertEquals(65.0, product.getCurrentStock());
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void validateExitSlip_alreadyValidatedSlip_throwsException() {
        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.VALIDATED);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));

        assertThrows(RuntimeException.class, () -> exitSlipService.validateExitSlip(1L));
        
        verify(stockMovementRepository, never()).save(any());
        verify(stockSlotRepository, never()).save(any());
    }

    @Test
    void validateExitSlip_complexScenarioWithFIFO_correctlyProcessesAllChanges() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Complex Product");
        product.setCurrentStock(150.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(1L);
        slot1.setProduct(product);
        slot1.setAvailableQuantity(40.0);
        slot1.setUnitPrice(10.00);
        slot1.setEntryDate(LocalDateTime.now().minusDays(30));

        StockSlot slot2 = new StockSlot();
        slot2.setId(2L);
        slot2.setProduct(product);
        slot2.setAvailableQuantity(60.0);
        slot2.setUnitPrice(12.00);
        slot2.setEntryDate(LocalDateTime.now().minusDays(20));

        StockSlot slot3 = new StockSlot();
        slot3.setId(3L);
        slot3.setProduct(product);
        slot3.setAvailableQuantity(50.0);
        slot3.setUnitPrice(15.00);
        slot3.setEntryDate(LocalDateTime.now().minusDays(10));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(1L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(80));

        ExitSlip slip = new ExitSlip();
        slip.setId(1L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.setValidatedAt(null);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(Arrays.asList(slot1, slot2, slot3));

        LocalDateTime beforeValidation = LocalDateTime.now();
        exitSlipService.validateExitSlip(1L);
        LocalDateTime afterValidation = LocalDateTime.now();

        assertEquals(ExitSlipStatus.VALIDATED, slip.getStatus());
        
        assertNotNull(slip.getValidatedAt());
        assertTrue(slip.getValidatedAt().isAfter(beforeValidation.minusSeconds(1)));
        assertTrue(slip.getValidatedAt().isBefore(afterValidation.plusSeconds(1)));

        assertEquals(0.0, slot1.getAvailableQuantity());
        assertEquals(20.0, slot2.getAvailableQuantity());
        assertEquals(50.0, slot3.getAvailableQuantity());

        assertEquals(70.0, product.getCurrentStock());

        ArgumentCaptor<StockMovement> movementCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(movementCaptor.capture());
        
        List<StockMovement> movements = movementCaptor.getAllValues();
        assertEquals(2, movements.size());
        
        StockMovement movement1 = movements.get(0);
        assertEquals(slot1, movement1.getStockSlot());
        assertEquals(-40.0, movement1.getQuantity());
        assertEquals(StockMovement.Type.out, movement1.getType());
        
        StockMovement movement2 = movements.get(1);
        assertEquals(slot2, movement2.getStockSlot());
        assertEquals(-40.0, movement2.getQuantity());
        assertEquals(StockMovement.Type.out, movement2.getType());

        verify(exitSlipRepository, times(1)).save(slip);
        verify(stockSlotRepository, times(1)).save(slot1);
        verify(stockSlotRepository, times(1)).save(slot2);
        verify(productRepository, times(1)).save(product);
    }
}
