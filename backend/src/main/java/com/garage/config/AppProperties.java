package com.garage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cookie cookie,
        String frontUrl,
        String corsOrigin,
        String mailFrom,
        int resetTokenMinutes,
        int exportMaxRows,
        Admin admin,
        Garage garage,
        InvoiceSettings invoice) {

    public record Jwt(String secret, int sessionHours, int rememberDays) {
    }

    public record Cookie(String name, boolean secure) {
    }

    public record Admin(String email, String password) {
    }

    /**
     * capacity: maximum number of appointments the garage can handle at the same time.
     * company: letterhead and payment details printed on the invoice PDF.
     */
    public record Garage(int capacity, Company company) {
    }

    /** Garage details printed on invoices. The default values in application.yml are fictitious examples. */
    public record Company(String name, String address, String postalCode, String city, String country,
                          String vatNumber, String iban, String bic, String email, String phone) {
    }

    /** vatRate: VAT percentage applied to the invoice total (the quote amounts are excluding VAT), e.g. 21. */
    public record InvoiceSettings(java.math.BigDecimal vatRate) {
    }
}
