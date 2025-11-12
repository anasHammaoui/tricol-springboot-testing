package com.example.tricol.tricolspringbootrestapi.service.impl;

import com.example.tricol.tricolspringbootrestapi.dto.response.ExitSlipResponse;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        // default save behaviors: return the same entity
        when(stockSlotRepository.save(any(StockSlot.class))).thenAnswer(i -> i.getArgument(0));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
        when(stockMovementRepository.save(any(StockMovement.class))).thenAnswer(i -> i.getArgument(0));
        when(exitSlipRepository.save(any(ExitSlip.class))).thenAnswer(i -> i.getArgument(0));
        when(exitSlipMapper.toResponse(any(ExitSlip.class))).thenReturn(new ExitSlipResponse());
    }

    @Test
    void validateExitSlip_partialConsumeSingleSlot_updatesSlotAndProductAndCreatesMovement() {
        // Arrange: one product, one slot with available 30, request 10
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
        slip.getItems().add(item);
        item.setExitSlip(slip);

        when(exitSlipRepository.findById(1001L)).thenReturn(Optional.of(slip));
        when(stockSlotRepository.findByProductAndAvailableQuantityGreaterThanOrderByEntryDateAsc(eq(product), eq(0.0)))
                .thenReturn(List.of(slot));


        // Assert slot available decreased by 10
        assertEquals(20.0, slot.getAvailableQuantity());

        // Assert product currentStock decreased by 10
        assertEquals(40.0, product.getCurrentStock());

        // Verify a stock movement was created with negative quantity -10
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(1)).save(captor.capture());
        StockMovement savedMovement = captor.getValue();
        assertEquals( -10.0, savedMovement.getQuantity());
        assertEquals(slot, savedMovement.getStockSlot());
        assertEquals(product, savedMovement.getProduct());
    }
}
