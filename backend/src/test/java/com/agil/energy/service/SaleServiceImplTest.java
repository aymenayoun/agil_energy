package com.agil.energy.service;

import com.agil.energy.dto.request.CreateSaleRequest;
import com.agil.energy.dto.response.SaleResponse;
import com.agil.energy.entity.*;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.impl.SaleServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleServiceImplTest {

    @Mock private SaleRepository saleRepository;
    @Mock private StationRepository stationRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private TankRepository tankRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private EntityMapper mapper;
    @Mock private AlertCheckService alertCheckService;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private SaleAnomalyDetector saleAnomalyDetector;
    @Mock private CurrentUserContext ctx;

    @InjectMocks private SaleServiceImpl saleService;

    private Station station;
    private FuelType fuelType;
    private Tank tank;
    private CreateSaleRequest validRequest;

    @BeforeEach
    void setUp() {
        station  = new Station(); station.setId(1L); station.setName("Station Tunis");
        fuelType = new FuelType(); fuelType.setId(1L); fuelType.setName("Gasoil");

        tank = Tank.builder()
                .id(1L)
                .station(station)
                .fuelType(fuelType)
                .capacity(new BigDecimal("20000"))
                .currentStock(new BigDecimal("5000"))
                .criticalThreshold(new BigDecimal("2000"))
                .build();

        validRequest = new CreateSaleRequest();
        validRequest.setStationId(1L);
        validRequest.setFuelTypeId(1L);
        validRequest.setSaleDate(LocalDate.now().minusDays(1));
        validRequest.setQuantity(new BigDecimal("500"));
    }

    @Test
    @DisplayName("createSale — successfully creates sale and reduces stock")
    void createSale_valid_reducesStock() {
        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(1L, 1L, validRequest.getSaleDate()))
                .thenReturn(false);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.findByStationIdAndFuelTypeIdForUpdate(1L, 1L)).thenReturn(Optional.of(tank));
        when(saleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toSaleResponse(any())).thenReturn(new SaleResponse());

        saleService.createSale(validRequest);

        assertThat(tank.getCurrentStock()).isEqualByComparingTo("4500");
        verify(tankRepository).save(tank);
        verify(stockMovementRepository).save(any(StockMovement.class));
    }

    @Test
    @DisplayName("createSale — throws BusinessException for future sale date")
    void createSale_futureDate_throwsException() {
        validRequest.setSaleDate(LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> saleService.createSale(validRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("futur");
    }

    @Test
    @DisplayName("createSale — throws DuplicateResourceException for duplicate entry")
    void createSale_duplicate_throwsException() {
        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> saleService.createSale(validRequest))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("createSale — throws BusinessException when stock is insufficient")
    void createSale_insufficientStock_throwsException() {
        validRequest.setQuantity(new BigDecimal("99999"));

        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(any(), any(), any()))
                .thenReturn(false);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.findByStationIdAndFuelTypeIdForUpdate(1L, 1L)).thenReturn(Optional.of(tank));

        assertThatThrownBy(() -> saleService.createSale(validRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("insuffisant");
    }

    @Test
    @DisplayName("createSale — throws ResourceNotFoundException for unknown station")
    void createSale_unknownStation_throwsException() {
        when(saleRepository.existsByStationIdAndFuelTypeIdAndSaleDate(any(), any(), any()))
                .thenReturn(false);
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());

        validRequest.setStationId(99L);

        assertThatThrownBy(() -> saleService.createSale(validRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}