package com.legalfam.backend.payment.infrastructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.legalfam.backend.payment.application.dto.PaymentPlanDefinition;
import com.legalfam.backend.payment.domain.exception.InvalidPaymentRequestException;
import com.legalfam.backend.payment.domain.exception.PaymentApiError;
import com.legalfam.backend.payment.domain.model.SubscriptionPlanCode;
import com.legalfam.backend.payment.infrastructure.config.PaymentPlanProperties;
import com.legalfam.backend.payment.infrastructure.config.PaymentPlanProperties.Plan;
import org.junit.jupiter.api.Test;

class PaymentPlanCatalogAdapterTest {

    private static final Plan FREE = new Plan("Gratuito", null, 50, 0, null, 4, 30);
    private static final Plan BASIC = new Plan(" Básico ", "Para empezar", 200, 1990, "PEN", 8, null);
    private static final Plan PREMIUM = new Plan("Premium", "Todo", 500, 0, "pen", 12, null);

    private final PaymentPlanCatalogAdapter catalog =
            new PaymentPlanCatalogAdapter(new PaymentPlanProperties(FREE, BASIC, PREMIUM));

    @Test
    void listsPlansWithNormalizedDefaults() {
        assertThat(catalog.listPlans()).extracting(PaymentPlanDefinition::code)
                .containsExactly(SubscriptionPlanCode.FREE, SubscriptionPlanCode.BASIC, SubscriptionPlanCode.PREMIUM);
        assertThat(catalog.getFreePlan().description()).isEmpty();
        assertThat(catalog.getFreePlan().currency()).isEqualTo("pen");
        assertThat(catalog.listPlans().get(1).displayName()).isEqualTo("Básico");
        assertThat(catalog.listPlans().get(1).currency()).isEqualTo("pen");
    }

    @Test
    void resolvesPaidPlanIgnoringCaseAndSpaces() {
        assertThat(catalog.getPaidPlanOrThrow(" basic ").code()).isEqualTo(SubscriptionPlanCode.BASIC);
    }

    @Test
    void rejectsInvalidPlanRequests() {
        assertError(null, PaymentApiError.PLAN_CODE_REQUIRED);
        assertError(" ", PaymentApiError.PLAN_CODE_REQUIRED);
        assertError("gold", PaymentApiError.PLAN_CODE_INVALID);
        assertError("free", PaymentApiError.PAID_PLAN_REQUIRED);
        assertError("premium", PaymentApiError.PLAN_NOT_PURCHASABLE);
    }

    @Test
    void rejectsMisconfiguredPlans() {
        assertMisconfigured(new Plan(" ", null, 50, 0, null, 4, null));
        assertMisconfigured(new Plan("Gratis", null, -1, 0, null, 4, null));
        assertMisconfigured(new Plan("Gratis", null, 50, -1, null, 4, null));
        assertMisconfigured(new Plan("Gratis", null, 50, 0, null, 0, null));
        assertMisconfigured(new Plan("Gratis", null, 50, 0, null, 4, 0));
    }

    private void assertError(String code, PaymentApiError expected) {
        assertThatThrownBy(() -> catalog.getPaidPlanOrThrow(code))
                .isInstanceOfSatisfying(InvalidPaymentRequestException.class,
                        ex -> assertThat(ex.error()).isEqualTo(expected));
    }

    private static void assertMisconfigured(Plan free) {
        assertThatThrownBy(() -> new PaymentPlanCatalogAdapter(new PaymentPlanProperties(free, BASIC, PREMIUM)))
                .isInstanceOf(IllegalStateException.class);
    }
}
