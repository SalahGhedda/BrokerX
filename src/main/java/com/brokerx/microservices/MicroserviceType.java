package com.brokerx.microservices;

public enum MicroserviceType {
    ORDERS,
    PORTFOLIO,
    MARKETDATA,
    REPORTING;

    public static MicroserviceType fromEnvironment() {
        String value = System.getenv().getOrDefault("BROKERX_SERVICE", "").trim();
        if (value.isEmpty()) {
            return ORDERS;
        }
        return MicroserviceType.valueOf(value.toUpperCase());
    }
}
