package com.agil.energy.service;

import com.agil.energy.dto.request.CreateDeliveryRequest;
import com.agil.energy.dto.response.DeliveryResponse;
import com.agil.energy.entity.*;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.impl.DeliveryServiceImpl;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceImplTest {

    @Mock private DeliveryRepository deliveryRepository;
    @Mock private StationRepository stationRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private TankRepository tankRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityMapper mapper;
    @Mock private AlertCheckService alertCheckService;
    @Mock private AuditService auditService;
    @Mock private ObjectMapper objectMapper;
    @Mock private CurrentUserContext ctx;

    @InjectMocks private DeliveryServiceImpl deliveryService;

    private Station station;
    private FuelType fuelType;
    private Tank tank;
    private CreateDeliveryRequest validRequest;

    @BeforeEach
    void setUp() {
        station = new Station();
        station.setId(1L);
        station.setName("Station Tunis");

        fuelType = new FuelType();
        fuelType.setId(1L);
        fuelType.setName("Gasoil");

        tank = Tank.builder()
                .id(1L)
                .station(station)
                .fuelType(fuelType)
                .capacity(new BigDecimal("20000"))
                .currentStock(new BigDecimal("5000"))
                .criticalThreshold(new BigDecimal("2000"))
                .build();

        validRequest = new CreateDeliveryRequest();
        validRequest.setStationId(1L);
        validRequest.setFuelTypeId(1L);
        validRequest.setDeliveryDate(LocalDate.now());
        validRequest.setQuantity(new BigDecimal("3000"));
    }

    @Test
    @DisplayName("createDelivery — successfully adds stock")
    void createDelivery_valid_addsStock() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.findByStationIdAndFuelTypeIdForUpdate(1L, 1L)).thenReturn(Optional.of(tank));
        when(deliveryRepository.save(any())).thenAnswer(inv -> {
            Delivery d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });
        when(mapper.toDeliveryResponse(any())).thenReturn(new DeliveryResponse());

        deliveryService.createDelivery(validRequest);

        assertThat(tank.getCurrentStock()).isEqualByComparingTo("8000");
        verify(tankRepository).save(tank);
        verify(stockMovementRepository).save(any(StockMovement.class));
        verify(alertCheckService).checkStockAlerts(1L, 1L);
    }

    @Test
    @DisplayName("createDelivery — throws BusinessException when exceeding capacity")
    void createDelivery_exceedsCapacity_throwsException() {
        validRequest.setQuantity(new BigDecimal("99999"));

        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.findByStationIdAndFuelTypeIdForUpdate(1L, 1L)).thenReturn(Optional.of(tank));

        assertThatThrownBy(() -> deliveryService.createDelivery(validRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("capacité");
    }

    @Test
    @DisplayName("createDelivery — throws ResourceNotFoundException for unknown station")
    void createDelivery_unknownStation_throwsException() {
        validRequest.setStationId(99L);
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.createDelivery(validRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("validateDelivery — throws BusinessException if already validated")
    void validateDelivery_alreadyValidated_throwsException() {
        Delivery delivery = new Delivery();
        delivery.setId(1L);
        delivery.setValidated(true);
        delivery.setStation(station);

        when(deliveryRepository.findById(1L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.validateDelivery(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà validée");
    }

    @Test
    @DisplayName("getDeliveriesByStation — returns mapped list")
    void getDeliveriesByStation_returnsList() {
        Delivery delivery = new Delivery();
        delivery.setId(1L);

        when(deliveryRepository.findByStationIdOrderByDeliveryDateDesc(1L)).thenReturn(List.of(delivery));
        when(mapper.toDeliveryResponse(delivery)).thenReturn(new DeliveryResponse());

        List<DeliveryResponse> result = deliveryService.getDeliveriesByStation(1L);

        assertThat(result).hasSize(1);
    }
}