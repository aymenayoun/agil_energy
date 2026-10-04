package com.agil.energy.service;

import com.agil.energy.dto.request.CreateDeliveryRequest;
import com.agil.energy.dto.response.DeliveryResponse;

import java.util.List;

public interface DeliveryService {

    DeliveryResponse createDelivery(CreateDeliveryRequest request);

    DeliveryResponse validateDelivery(Long id);

    List<DeliveryResponse> getDeliveriesByStation(Long stationId);

    List<DeliveryResponse> getAllDeliveries();
}