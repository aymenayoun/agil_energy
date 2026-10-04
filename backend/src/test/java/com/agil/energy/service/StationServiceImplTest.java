package com.agil.energy.service;

import com.agil.energy.dto.request.CreateStationRequest;
import com.agil.energy.dto.request.CreateTankRequest;
import com.agil.energy.dto.response.StationResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.*;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.*;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.impl.StationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationServiceImplTest {

    @Mock private StationRepository stationRepository;
    @Mock private UserRepository userRepository;
    @Mock private TankRepository tankRepository;
    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private EntityMapper mapper;
    @Mock private AuditService auditService;
    @Mock private CurrentUserContext ctx;

    @InjectMocks private StationServiceImpl stationService;

    private Station station;
    private StationResponse stationResponse;

    @BeforeEach
    void setUp() {
        station = new Station();
        station.setId(1L);
        station.setName("Station Tunis");
        station.setRegion("Tunis");
        station.setStatus(StationStatus.ACTIVE);

        stationResponse = new StationResponse();
        stationResponse.setId(1L);
        stationResponse.setName("Station Tunis");
    }

    @Test
    @DisplayName("createStation — creates station with ACTIVE status")
    void createStation_success() {
        CreateStationRequest request = new CreateStationRequest();
        request.setName("Station Tunis");
        request.setRegion("Tunis");
        request.setAddress("Av. Habib Bourguiba");

        when(stationRepository.save(any(Station.class))).thenReturn(station);
        when(mapper.toStationResponse(any())).thenReturn(stationResponse);

        StationResponse result = stationService.createStation(request);

        assertThat(result.getName()).isEqualTo("Station Tunis");
        verify(stationRepository).save(any(Station.class));
        verify(auditService).log(any(), eq("CREATE_STATION"), eq("Station"), any(), any(), any());
    }

    @Test
    @DisplayName("getStationById — throws ResourceNotFoundException for unknown ID")
    void getStationById_unknown_throwsException() {
        // ctx.requireAccessTo is void — on a mock it's a no-op by default
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stationService.getStationById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getAllStations — returns mapped list")
    void getAllStations_returnsList() {
        // Simulate ADMIN scope (null = no filter)
        when(ctx.scopedStationIds()).thenReturn(null);
        when(stationRepository.findAll()).thenReturn(List.of(station));
        when(mapper.toStationResponse(station)).thenReturn(stationResponse);

        List<StationResponse> result = stationService.getAllStations();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Station Tunis");
    }

    @Test
    @DisplayName("deactivateStation — sets status to INACTIVE")
    void deactivateStation_setsInactive() {
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));

        stationService.deactivateStation(1L);

        assertThat(station.getStatus()).isEqualTo(StationStatus.INACTIVE);
        verify(stationRepository).save(station);
    }

    @Test
    @DisplayName("activateStation — sets status to ACTIVE")
    void activateStation_setsActive() {
        station.setStatus(StationStatus.INACTIVE);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));

        stationService.activateStation(1L);

        assertThat(station.getStatus()).isEqualTo(StationStatus.ACTIVE);
    }

    @Test
    @DisplayName("addTank — throws DuplicateResourceException if tank exists for station+fuel")
    void addTank_duplicate_throwsException() {
        CreateTankRequest request = new CreateTankRequest();
        request.setStationId(1L);
        request.setFuelTypeId(1L);
        request.setCapacity(new BigDecimal("20000"));
        request.setCurrentStock(new BigDecimal("5000"));
        request.setCriticalThreshold(new BigDecimal("2000"));

        FuelType fuelType = new FuelType();
        fuelType.setId(1L);
        fuelType.setName("Gasoil");

        // ctx.requireAccessTo is void — no-op on mock by default
        when(stationRepository.findById(1L)).thenReturn(Optional.of(station));
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.findByStationIdAndFuelTypeId(1L, 1L)).thenReturn(Optional.of(new Tank()));

        assertThatThrownBy(() -> stationService.addTank(request))
                .isInstanceOf(DuplicateResourceException.class);
    }
}