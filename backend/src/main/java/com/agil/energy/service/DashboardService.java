package com.agil.energy.service;

import com.agil.energy.dto.response.DashboardResponse;

public interface DashboardService {
    DashboardResponse getDashboard(Long stationId);
}