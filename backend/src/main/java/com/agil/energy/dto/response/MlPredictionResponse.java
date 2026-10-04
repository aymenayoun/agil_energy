package com.agil.energy.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlPredictionResponse {

    @JsonProperty("forecast_7_days")
    private List<BigDecimal> forecast7Days;

    @JsonProperty("anomaly_score")
    private BigDecimal anomalyScore;

    @JsonProperty("risk_level")
    private String riskLevel;
}