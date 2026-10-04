package com.agil.energy.service;

import com.agil.energy.dto.request.CreateFuelTypeRequest;
import com.agil.energy.dto.request.UpdateFuelTypeRequest;
import com.agil.energy.entity.FuelType;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.repository.FuelTypeRepository;
import com.agil.energy.repository.TankRepository;
import com.agil.energy.service.impl.FuelTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FuelTypeServiceImplTest {

    @Mock private FuelTypeRepository fuelTypeRepository;
    @Mock private TankRepository tankRepository;
    @Mock private AuditService auditService;

    @InjectMocks private FuelTypeServiceImpl fuelTypeService;

    private FuelType fuelType;

    @BeforeEach
    void setUp() {
        fuelType = new FuelType();
        fuelType.setId(1L);
        fuelType.setName("Gasoil");
        fuelType.setDescription("Carburant diesel");
    }

    @Test
    @DisplayName("createFuelType — creates and audits when name is unique")
    void createFuelType_success() {
        CreateFuelTypeRequest request = new CreateFuelTypeRequest();
        request.setName("Gasoil");
        request.setDescription("Carburant diesel");

        when(fuelTypeRepository.existsByName("Gasoil")).thenReturn(false);
        when(fuelTypeRepository.save(any(FuelType.class))).thenReturn(fuelType);

        FuelType result = fuelTypeService.createFuelType(request);

        assertThat(result.getName()).isEqualTo("Gasoil");
        verify(fuelTypeRepository).save(any(FuelType.class));
        verify(auditService).log(any(), eq("CREATE_FUEL_TYPE"), eq("FuelType"), eq(1L), any(), any());
    }

    @Test
    @DisplayName("createFuelType — throws DuplicateResourceException when name exists")
    void createFuelType_duplicate_throwsException() {
        CreateFuelTypeRequest request = new CreateFuelTypeRequest();
        request.setName("Gasoil");

        when(fuelTypeRepository.existsByName("Gasoil")).thenReturn(true);

        assertThatThrownBy(() -> fuelTypeService.createFuelType(request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(fuelTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFuelType — updates name and description")
    void updateFuelType_success() {
        UpdateFuelTypeRequest request = new UpdateFuelTypeRequest();
        request.setName("Gasoil 50");
        request.setDescription("Diesel basse teneur en soufre");

        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(fuelTypeRepository.findByName("Gasoil 50")).thenReturn(Optional.empty());
        when(fuelTypeRepository.save(any(FuelType.class))).thenReturn(fuelType);

        FuelType result = fuelTypeService.updateFuelType(1L, request);

        assertThat(result.getName()).isEqualTo("Gasoil 50");
        assertThat(result.getDescription()).isEqualTo("Diesel basse teneur en soufre");
        verify(auditService).log(any(), eq("UPDATE_FUEL_TYPE"), eq("FuelType"), eq(1L), any(), any());
    }

    @Test
    @DisplayName("updateFuelType — throws ResourceNotFoundException for unknown ID")
    void updateFuelType_unknown_throwsException() {
        UpdateFuelTypeRequest request = new UpdateFuelTypeRequest();
        request.setName("X");
        when(fuelTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fuelTypeService.updateFuelType(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateFuelType — throws DuplicateResourceException when name belongs to another record")
    void updateFuelType_duplicateName_throwsException() {
        UpdateFuelTypeRequest request = new UpdateFuelTypeRequest();
        request.setName("SP95");

        FuelType other = new FuelType();
        other.setId(2L);
        other.setName("SP95");

        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(fuelTypeRepository.findByName("SP95")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> fuelTypeService.updateFuelType(1L, request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(fuelTypeRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFuelType — keeping same name does not trigger duplicate check")
    void updateFuelType_sameName_succeeds() {
        UpdateFuelTypeRequest request = new UpdateFuelTypeRequest();
        request.setName("Gasoil");
        request.setDescription("Mise à jour description");

        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(fuelTypeRepository.save(any(FuelType.class))).thenReturn(fuelType);

        FuelType result = fuelTypeService.updateFuelType(1L, request);

        assertThat(result.getDescription()).isEqualTo("Mise à jour description");
        verify(fuelTypeRepository, never()).findByName(anyString());
    }

    @Test
    @DisplayName("getFuelTypeById — throws ResourceNotFoundException for unknown ID")
    void getFuelTypeById_unknown_throwsException() {
        when(fuelTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fuelTypeService.getFuelTypeById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getAllFuelTypes — returns list")
    void getAllFuelTypes_returnsList() {
        when(fuelTypeRepository.findAll()).thenReturn(List.of(fuelType));

        List<FuelType> result = fuelTypeService.getAllFuelTypes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Gasoil");
    }

    @Test
    @DisplayName("deleteFuelType — deletes and audits when not referenced by a tank")
    void deleteFuelType_success() {
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.existsByFuelTypeId(1L)).thenReturn(false);

        fuelTypeService.deleteFuelType(1L);

        verify(fuelTypeRepository).delete(fuelType);
        verify(auditService).log(any(), eq("DELETE_FUEL_TYPE"), eq("FuelType"), eq(1L), any(), any());
    }

    @Test
    @DisplayName("deleteFuelType — throws BusinessException when referenced by a tank")
    void deleteFuelType_referenced_throwsException() {
        when(fuelTypeRepository.findById(1L)).thenReturn(Optional.of(fuelType));
        when(tankRepository.existsByFuelTypeId(1L)).thenReturn(true);

        assertThatThrownBy(() -> fuelTypeService.deleteFuelType(1L))
                .isInstanceOf(BusinessException.class);
        verify(fuelTypeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteFuelType — throws ResourceNotFoundException for unknown ID")
    void deleteFuelType_unknown_throwsException() {
        when(fuelTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fuelTypeService.deleteFuelType(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}