package com.legalfam.backend.payment.infrastructure.config;

import com.legalfam.backend.payment.application.service.PaymentCheckoutProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Bean
    public PaymentCheckoutProperties paymentCheckoutProperties(MercadoPagoProperties mercadoPagoProperties) {
        return new PaymentCheckoutProperties(
                mercadoPagoProperties.normalizedCheckoutSuccessUrl(),
                mercadoPagoProperties.normalizedCheckoutCancelUrl()
        );
    }
}
