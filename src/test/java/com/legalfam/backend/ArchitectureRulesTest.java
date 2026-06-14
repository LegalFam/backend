package com.legalfam.backend;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "com.legalfam.backend",
        importOptions = {ImportOption.DoNotIncludeTests.class}
)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "io.swagger..",
                    "org.springdoc.."
            );

    @ArchTest
    static final ArchRule application_must_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule auth_application_must_not_depend_on_other_applications = noClasses()
            .that().resideInAPackage("com.legalfam.backend.auth.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.chat.application..",
                    "com.legalfam.backend.payment.application..",
                    "com.legalfam.backend.user.application.."
            );

    @ArchTest
    static final ArchRule chat_application_must_not_depend_on_other_applications = noClasses()
            .that().resideInAPackage("com.legalfam.backend.chat.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.auth.application..",
                    "com.legalfam.backend.payment.application..",
                    "com.legalfam.backend.user.application.."
            );

    @ArchTest
    static final ArchRule payment_application_must_not_depend_on_other_applications = noClasses()
            .that().resideInAPackage("com.legalfam.backend.payment.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.auth.application..",
                    "com.legalfam.backend.chat.application..",
                    "com.legalfam.backend.user.application.."
            );

    @ArchTest
    static final ArchRule common_must_not_depend_on_feature_infrastructure = noClasses()
            .that().resideInAPackage("com.legalfam.backend.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.auth.infrastructure..",
                    "com.legalfam.backend.chat.infrastructure..",
                    "com.legalfam.backend.payment.infrastructure.."
            );

    @ArchTest
    static final ArchRule auth_must_not_depend_on_other_module_infrastructure = noClasses()
            .that().resideInAPackage("com.legalfam.backend.auth..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.chat.infrastructure..",
                    "com.legalfam.backend.payment.infrastructure.."
            );

    @ArchTest
    static final ArchRule chat_must_not_depend_on_other_module_infrastructure = noClasses()
            .that().resideInAPackage("com.legalfam.backend.chat..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.auth.infrastructure..",
                    "com.legalfam.backend.payment.infrastructure.."
            );

    @ArchTest
    static final ArchRule payment_must_not_depend_on_other_module_infrastructure = noClasses()
            .that().resideInAPackage("com.legalfam.backend.payment..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.legalfam.backend.auth.infrastructure..",
                    "com.legalfam.backend.chat.infrastructure.."
            );
}
