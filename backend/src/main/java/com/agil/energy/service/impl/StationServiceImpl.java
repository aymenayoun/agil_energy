package com.agil.energy.service.impl;

import com.agil.energy.dto.request.CreateStationRequest;
import com.agil.energy.dto.request.CreateTankRequest;
import com.agil.energy.dto.request.UpdateStationRequest;
import com.agil.energy.dto.response.StationResponse;
import com.agil.energy.dto.response.TankResponse;
import com.agil.energy.entity.FuelType;
import com.agil.energy.entity.Station;
import com.agil.energy.entity.Tank;
import com.agil.energy.entity.User;
import com.agil.energy.enums.StationStatus;
import com.agil.energy.enums.UserStatus;
import com.agil.energy.exception.BusinessException;
import com.agil.energy.exception.DuplicateResourceException;
import com.agil.energy.exception.ResourceNotFoundException;
import com.agil.energy.mapper.EntityMapper;
import com.agil.energy.repository.FuelTypeRepository;
import com.agil.energy.repository.StationRepository;
import com.agil.energy.repository.TankRepository;
import com.agil.energy.repository.UserRepository;
import com.agil.energy.security.CurrentUserContext;
import com.agil.energy.service.AuditService;
import com.agil.energy.service.StationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class StationServiceImpl implements StationService {

    private static final String STATION_MANAGER = "STATION_MANAGER";

    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final TankRepository tankRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final EntityMapper mapper;
    private final AuditService auditService;
    private final CurrentUserContext ctx;

    @Override
    public StationResponse createStation(CreateStationRequest request) {
        Station station = Station.builder()
                .name(request.getName())
                .region(request.getRegion())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(StationStatus.ACTIVE)
                .build();

        Station saved = stationRepository.save(station);

        if (request.getManagerId() != null) {
            assignManager(saved, request.getManagerId());
        }

        auditService.log(null, "CREATE_STATION", "Station", saved.getId(),
                String.format("{\"name\":\"%s\",\"region\":\"%s\"}", saved.getName(), saved.getRegion()), null);

        return mapper.toStationResponse(saved);
    }

    @Override
    public StationResponse updateStation(Long id, UpdateStationRequest request) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", id));

        if (request.getName() != null)      station.setName(request.getName());
        if (request.getRegion() != null)    station.setRegion(request.getRegion());
        if (request.getAddress() != null)   station.setAddress(request.getAddress());
        if (request.getLatitude() != null)  station.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) station.setLongitude(request.getLongitude());

        Station saved = stationRepository.save(station);

        if (request.getManagerId() != null) {
            assignManager(saved, request.getManagerId());
        }

        return mapper.toStationResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public StationResponse getStationById(Long id) {
        ctx.requireAccessTo(id);
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", id));
        return mapper.toStationResponse(station);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResponse> getAllStations() {
        // scopedStationIds() returns:
        //   null          → ADMIN (no filter)
        //   [id1,id2,...] → MANAGER (region stations)
        //   [id]          → STATION_MANAGER (single station)
        //   []            → no access
        List<Long> scopedIds = ctx.scopedStationIds();

        List<Station> stations;
        if (scopedIds == null) {
            // ADMIN: all stations
            stations = stationRepository.findAll();
        } else if (scopedIds.isEmpty()) {
            return Collections.emptyList();
        } else {
            stations = stationRepository.findByIdIn(scopedIds);
        }

        return stations.stream()
                .map(mapper::toStationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StationResponse> getStationsByRegion(String region) {
        List<Long> scopedIds = ctx.scopedStationIds();

        List<Station> stations = stationRepository.findByRegion(region);

        // If scoped, intersect with the user's allowed stations
        if (scopedIds != null) {
            stations = stations.stream()
                    .filter(s -> scopedIds.contains(s.getId()))
                    .collect(Collectors.toList());
        }

        return stations.stream()
                .map(mapper::toStationResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void deactivateStation(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", id));
        station.setStatus(StationStatus.INACTIVE);
        stationRepository.save(station);
        auditService.log(null, "DEACTIVATE_STATION", "Station", id, null, null);
    }

    @Override
    public void activateStation(Long id) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", id));
        station.setStatus(StationStatus.ACTIVE);
        stationRepository.save(station);
        auditService.log(null, "ACTIVATE_STATION", "Station", id, null, null);
    }

    @Override
    public TankResponse addTank(CreateTankRequest request) {
        ctx.requireAccessTo(request.getStationId());

        Station station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Station", request.getStationId()));

        FuelType fuelType = fuelTypeRepository.findById(request.getFuelTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Type de carburant", request.getFuelTypeId()));

        if (tankRepository.findByStationIdAndFuelTypeId(request.getStationId(), request.getFuelTypeId()).isPresent()) {
            throw new DuplicateResourceException(
                    "Un réservoir pour " + fuelType.getName() + " existe déjà dans cette station");
        }

        Tank tank = Tank.builder()
                .station(station)
                .fuelType(fuelType)
                .capacity(request.getCapacity())
                .currentStock(request.getCurrentStock())
                .criticalThreshold(request.getCriticalThreshold())
                .build();

        auditService.log(null, "ADD_TANK", "Tank", tank.getId(),
                String.format("{\"station\":%d,\"fuel\":%d,\"capacity\":%s}",
                        request.getStationId(), request.getFuelTypeId(), request.getCapacity()), null);
        return mapper.toTankResponse(tankRepository.save(tank));
    }

    // ---------- helpers ----------

    private void assignManager(Station station, Long managerId) {
        User candidate = userRepository.findById(managerId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", managerId));

        if (candidate.getRole() == null || !STATION_MANAGER.equals(candidate.getRole().getName())) {
            throw new BusinessException("Seul un utilisateur de rôle STATION_MANAGER peut gérer une station");
        }

        Optional<User> current = userRepository.findFirstByStationIdAndRoleNameAndStatus(
                station.getId(), STATION_MANAGER, UserStatus.ACTIVE);
        if (current.isPresent() && !current.get().getId().equals(managerId)) {
            throw new BusinessException(
                    "Cette station est déjà gérée par " + current.get().getName() +
                            ". Désassignez ou désactivez ce gérant avant d'en assigner un nouveau.");
        }

        candidate.setStation(station);
        userRepository.save(candidate);
    }
}