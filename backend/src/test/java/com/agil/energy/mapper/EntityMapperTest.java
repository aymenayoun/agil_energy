package com.agil.energy.mapper;

import com.agil.energy.dto.response.*;
import com.agil.energy.entity.*;
import com.agil.energy.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMapperTest {

    private EntityMapper mapper;

    // Shared fixtures
    private Role adminRole;
    private Role stationManagerRole;
    private Station station;
    private FuelType diesel;
    private Tank tank;
    private User adminUser;
    private User managerUser;

    @BeforeEach
    void setUp() {
        mapper = new EntityMapper();

        adminRole = new Role(); adminRole.setId(1L); adminRole.setName("ADMIN");
        stationManagerRole = new Role(); stationManagerRole.setId(3L); stationManagerRole.setName("STATION_MANAGER");

        station = new Station();
        station.setId(1L); station.setName("Station Nord");
        station.setRegion("Nord"); station.setAddress("123 Rue");
        station.setStatus(StationStatus.ACTIVE);
        station.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        diesel = new FuelType(); diesel.setId(1L); diesel.setName("Diesel");

        tank = new Tank();
        tank.setId(10L); tank.setStation(station); tank.setFuelType(diesel);
        tank.setCapacity(new BigDecimal("10000.00"));
        tank.setCurrentStock(new BigDecimal("3000.00"));
        tank.setCriticalThreshold(new BigDecimal("2000.00"));

        adminUser = new User();
        adminUser.setId(1L); adminUser.setName("Admin"); adminUser.setEmail("admin@agil.tn");
        adminUser.setRole(adminRole); adminUser.setStatus(UserStatus.ACTIVE);
        adminUser.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));

        managerUser = new User();
        managerUser.setId(2L); managerUser.setName("Manager");
        managerUser.setEmail("mgr@agil.tn"); managerUser.setRole(stationManagerRole);
        managerUser.setStatus(UserStatus.ACTIVE); managerUser.setStation(station);
    }

    // ── User mapping ──

    @Test
    @DisplayName("toUserResponse — maps all fields")
    void toUserResponse_mapsAll() {
        UserResponse r = mapper.toUserResponse(adminUser);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getName()).isEqualTo("Admin");
        assertThat(r.getEmail()).isEqualTo("admin@agil.tn");
        assertThat(r.getRoleName()).isEqualTo("ADMIN");
        assertThat(r.getStatus()).isEqualTo("ACTIVE");
        assertThat(r.getStationId()).isNull();
        assertThat(r.getStationName()).isNull();
    }

    @Test
    @DisplayName("toUserResponse — includes stationId when assigned")
    void toUserResponse_withStation() {
        UserResponse r = mapper.toUserResponse(managerUser);
        assertThat(r.getStationId()).isEqualTo(1L);
        assertThat(r.getStationName()).isEqualTo("Station Nord");
    }

    // ── Tank mapping ──

    @Test
    @DisplayName("toTankResponse — computes stockPercentage correctly")
    void toTankResponse_computesPercentage() {
        TankResponse r = mapper.toTankResponse(tank);
        assertThat(r.getId()).isEqualTo(10L);
        assertThat(r.getStationId()).isEqualTo(1L);
        assertThat(r.getStationName()).isEqualTo("Station Nord");
        assertThat(r.getFuelTypeId()).isEqualTo(1L);
        assertThat(r.getFuelTypeName()).isEqualTo("Diesel");
        assertThat(r.getCapacity()).isEqualByComparingTo("10000.00");
        assertThat(r.getCurrentStock()).isEqualByComparingTo("3000.00");
        // 3000/10000 * 100 = 30.00
        assertThat(r.getStockPercentage()).isEqualByComparingTo("30.00");
        assertThat(r.isCritical()).isFalse(); // 3000 > 2000
    }

    @Test
    @DisplayName("toTankResponse — critical when stock <= threshold")
    void toTankResponse_critical() {
        tank.setCurrentStock(new BigDecimal("1500.00"));
        TankResponse r = mapper.toTankResponse(tank);
        assertThat(r.isCritical()).isTrue();
    }

    @Test
    @DisplayName("toTankResponse — critical when stock equals threshold")
    void toTankResponse_criticalEqual() {
        tank.setCurrentStock(new BigDecimal("2000.00"));
        TankResponse r = mapper.toTankResponse(tank);
        assertThat(r.isCritical()).isTrue(); // <= threshold
    }

    @Test
    @DisplayName("toTankResponse — zero capacity gives 0 percentage")
    void toTankResponse_zeroCapacity() {
        tank.setCapacity(BigDecimal.ZERO);
        tank.setCurrentStock(BigDecimal.ZERO);
        TankResponse r = mapper.toTankResponse(tank);
        assertThat(r.getStockPercentage()).isEqualByComparingTo("0");
    }

    // ── Station mapping ──

    @Test
    @DisplayName("toStationResponse — maps basic fields")
    void toStationResponse_mapsFields() {
        station.setTanks(List.of(tank));
        station.setAssignedUsers(List.of());
        StationResponse r = mapper.toStationResponse(station);
        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getName()).isEqualTo("Station Nord");
        assertThat(r.getRegion()).isEqualTo("Nord");
        assertThat(r.getStatus()).isEqualTo("ACTIVE");
        assertThat(r.getManagerId()).isNull(); // no STATION_MANAGER in assignedUsers
        assertThat(r.getTanks()).hasSize(1);
    }

    @Test
    @DisplayName("toStationResponse — picks active STATION_MANAGER as manager")
    void toStationResponse_withManager() {
        station.setTanks(List.of());
        station.setAssignedUsers(List.of(managerUser));
        StationResponse r = mapper.toStationResponse(station);
        assertThat(r.getManagerId()).isEqualTo(2L);
        assertThat(r.getManagerName()).isEqualTo("Manager");
    }

    @Test
    @DisplayName("toStationResponse — ignores inactive STATION_MANAGER")
    void toStationResponse_ignoresInactiveManager() {
        managerUser.setStatus(UserStatus.INACTIVE);
        station.setTanks(List.of());
        station.setAssignedUsers(List.of(managerUser));
        StationResponse r = mapper.toStationResponse(station);
        assertThat(r.getManagerId()).isNull();
    }

    // ── Sale mapping ──

    @Test
    @DisplayName("toSaleResponse — maps all fields")
    void toSaleResponse_mapsAll() {
        Sale sale = Sale.builder()
                .id(5L).station(station).fuelType(diesel)
                .saleDate(LocalDate.of(2025, 3, 15))
                .quantity(new BigDecimal("500.00"))
                .validated(true)
                .build();
        sale.setCreatedAt(LocalDateTime.of(2025, 3, 15, 10, 0));

        SaleResponse r = mapper.toSaleResponse(sale);
        assertThat(r.getId()).isEqualTo(5L);
        assertThat(r.getStationId()).isEqualTo(1L);
        assertThat(r.getStationName()).isEqualTo("Station Nord");
        assertThat(r.getFuelTypeId()).isEqualTo(1L);
        assertThat(r.getFuelTypeName()).isEqualTo("Diesel");
        assertThat(r.getSaleDate()).isEqualTo(LocalDate.of(2025, 3, 15));
        assertThat(r.getQuantity()).isEqualByComparingTo("500.00");
        assertThat(r.getValidated()).isTrue();
    }

    // ── Alert mapping ──

    @Test
    @DisplayName("toAlertResponse — maps all fields including resolved info")
    void toAlertResponse_mapsAll() {
        Alert alert = Alert.builder()
                .id(7L).station(station)
                .alertType(AlertType.STOCK_RUPTURE)
                .severity(Severity.HIGH)
                .message("Stock bas")
                .status(AlertStatus.RESOLVED)
                .resolvedBy(adminUser)
                .resolvedAt(LocalDateTime.of(2025, 4, 1, 12, 0))
                .build();
        alert.setCreatedAt(LocalDateTime.of(2025, 3, 28, 8, 0));

        AlertResponse r = mapper.toAlertResponse(alert);
        assertThat(r.getId()).isEqualTo(7L);
        assertThat(r.getStationId()).isEqualTo(1L);
        assertThat(r.getAlertType()).isEqualTo("STOCK_RUPTURE");
        assertThat(r.getSeverity()).isEqualTo("HIGH");
        assertThat(r.getMessage()).isEqualTo("Stock bas");
        assertThat(r.getStatus()).isEqualTo("RESOLVED");
        assertThat(r.getResolvedByName()).isEqualTo("Admin");
    }

    @Test
    @DisplayName("toAlertResponse — null resolvedBy gives null name")
    void toAlertResponse_noResolver() {
        Alert alert = Alert.builder()
                .id(8L).station(station)
                .alertType(AlertType.ANOMALY)
                .severity(Severity.MEDIUM)
                .message("Anomalie")
                .status(AlertStatus.ACTIVE)
                .build();
        alert.setCreatedAt(LocalDateTime.now());

        AlertResponse r = mapper.toAlertResponse(alert);
        assertThat(r.getResolvedByName()).isNull();
        assertThat(r.getResolvedAt()).isNull();
    }

    // ── Delivery mapping ──

    @Test
    @DisplayName("toDeliveryResponse — maps all fields")
    void toDeliveryResponse_mapsAll() {
        Delivery delivery = new Delivery();
        delivery.setId(3L); delivery.setStation(station); delivery.setFuelType(diesel);
        delivery.setDeliveryDate(LocalDate.of(2025, 5, 10));
        delivery.setQuantity(new BigDecimal("8000.00"));
        delivery.setValidated(false);
        delivery.setCreatedBy(adminUser);
        delivery.setCreatedAt(LocalDateTime.of(2025, 5, 10, 9, 0));

        DeliveryResponse r = mapper.toDeliveryResponse(delivery);
        assertThat(r.getId()).isEqualTo(3L);
        assertThat(r.getStationId()).isEqualTo(1L);
        assertThat(r.getFuelTypeName()).isEqualTo("Diesel");
        assertThat(r.getQuantity()).isEqualByComparingTo("8000.00");
        assertThat(r.getValidated()).isFalse();
        assertThat(r.getCreatedByName()).isEqualTo("Admin");
    }

    @Test
    @DisplayName("toDeliveryResponse — null createdBy gives null name")
    void toDeliveryResponse_noCreator() {
        Delivery delivery = new Delivery();
        delivery.setId(4L); delivery.setStation(station); delivery.setFuelType(diesel);
        delivery.setDeliveryDate(LocalDate.of(2025, 5, 11));
        delivery.setQuantity(BigDecimal.TEN);
        delivery.setValidated(true);
        delivery.setCreatedBy(null);
        delivery.setCreatedAt(LocalDateTime.now());

        DeliveryResponse r = mapper.toDeliveryResponse(delivery);
        assertThat(r.getCreatedByName()).isNull();
    }
}