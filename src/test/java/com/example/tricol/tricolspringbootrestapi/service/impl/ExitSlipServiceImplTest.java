package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.dto.response.ExitSlipResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ExitSlipServiceImplTest {

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
        // default save behaviors: return the same entity (lenient for tests that throw exceptions)
        lenient().when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(exitSlipMapper.toResponse(any(ExitSlip.class))).thenReturn(new ExitSlipResponse());
    }

    @Test
    void validateExitSlip_partialConsumeSingleSlot_updatesSlotAndProductAndCreatesMovement() {
        Product product = new Product();
        product.setId(1L);
        product.setName("Widget");
        product.setCurrentStock(50.0);

        StockSlot slot = new StockSlot();
        slot.setId(11L);
        slot.setProduct(product);
        slot.setAvailableQuantity(30.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(5));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(101L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(10));

        ExitSlip slip = new ExitSlip();
        slip.setId(1001L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1001L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));

        exitSlipService.validateExitSlip(1001L);

        assertEquals(20.0, slot.getAvailableQuantity());

        assertEquals(40.0, product.getCurrentStock());

        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(captor.capture());
        StockMovement savedMovement = captor.getValue();
        assertEquals(-10.0, savedMovement.getQuantity());
        assertEquals(slot, savedMovement.getStockSlot());
        assertEquals(product, savedMovement.getProduct());
    }

    @Test
    void validateExitSlip_consumeMultipleSlots_updatesAllSlotsAndProduct() {
        Product product = new Product();
        product.setId(2L);
        product.setName("Gadget");
        product.setCurrentStock(100.0);

        StockSlot slotOld = new StockSlot();
        slotOld.setId(21L);
        slotOld.setProduct(product);
        slotOld.setAvailableQuantity(30.0);
        slotOld.setEntryDate(LocalDateTime.now().minusDays(10));

        StockSlot slotNew = new StockSlot();
        slotNew.setId(22L);
        slotNew.setProduct(product);
        slotNew.setAvailableQuantity(30.0);
        slotNew.setEntryDate(LocalDateTime.now().minusDays(2));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(201L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(50));

        ExitSlip slip = new ExitSlip();
        slip.setId(2001L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(2001L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(Arrays.asList(slotOld, slotNew));

        exitSlipService.validateExitSlip(2001L);

        assertEquals(0.0, slotOld.getAvailableQuantity());

        assertEquals(10.0, slotNew.getAvailableQuantity());

        assertEquals(50.0, product.getCurrentStock());

        verify(stockMovementRepository, times(2)).save(any(StockMovement.class));
    }

    @Test
    void validateExitSlip_insufficientStock_throwsException() {
        // Scénario 3: Sortie avec stock insuffisant
        Product product = new Product();
        product.setId(3L);
        product.setName("Scarce Item");
        product.setCurrentStock(20.0);

        StockSlot slot = new StockSlot();
        slot.setId(31L);
        slot.setProduct(product);
        slot.setAvailableQuantity(15.0);
        slot.setEntryDate(LocalDateTime.now().minusDays(3));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(301L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(50)); // Requesting more than available

        ExitSlip slip = new ExitSlip();
        slip.setId(3001L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(3001L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));

        // Should throw exception due to insufficient stock
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            exitSlipService.validateExitSlip(3001L);
        });

        assertEquals("Insufficient stock for product: Scarce Item. Required: 50.00, Available: 15.00",
                     exception.getMessage());

        // Verify no stock movements were created
        verify(stockMovementRepository, never()).save(any(StockMovement.class));

        // Verify product stock was not updated
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void validateExitSlip_exhaustExactStock_depletesSlotsCompletely() {
        // Scénario 4: Sortie épuisant exactement le stock disponible
        Product product = new Product();
        product.setId(4L);
        product.setName("Limited Stock");
        product.setCurrentStock(75.0);

        StockSlot slot1 = new StockSlot();
        slot1.setId(41L);
        slot1.setProduct(product);
        slot1.setAvailableQuantity(25.0);
        slot1.setEntryDate(LocalDateTime.now().minusDays(7));

        StockSlot slot2 = new StockSlot();
        slot2.setId(42L);
        slot2.setProduct(product);
        slot2.setAvailableQuantity(50.0);
        slot2.setEntryDate(LocalDateTime.now().minusDays(3));

        ExitSlipItem item = new ExitSlipItem();
        item.setId(401L);
        item.setProduct(product);
        item.setRequestedQuantity(BigDecimal.valueOf(75)); // Exact total available

        ExitSlip slip = new ExitSlip();
        slip.setId(4001L);
        slip.setStatus(ExitSlipStatus.DRAFT);
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(4001L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(Arrays.asList(slot1, slot2));

        exitSlipService.validateExitSlip(4001L);

        // Both slots should be completely depleted
        assertEquals(0.0, slot1.getAvailableQuantity());
        assertEquals(0.0, slot2.getAvailableQuantity());

        // Product stock should be zero
        assertEquals(0.0, product.getCurrentStock());

        // Two stock movements should be created
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());

        List<StockMovement> movements = captor.getAllValues();
        assertEquals(-25.0, movements.get(0).getQuantity());
        assertEquals(slot1, movements.get(0).getStockSlot());
        assertEquals(-50.0, movements.get(1).getQuantity());
        assertEquals(slot2, movements.get(1).getStockSlot());
    }
}
