package com.padelpro.auth.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * T-ArchUnit — Hexagonal architecture boundary verification for the entire platform.
 *
 * <p>Scans {@code com.padelpro} (auth + usuarios modules) to enforce hexagonal
 * boundaries across all bounded contexts.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li>domain must not depend on infrastructure</li>
 *   <li>domain must not depend on application services</li>
 *   <li>application services must not depend on infrastructure</li>
 *   <li>controllers live in infrastructure.web</li>
 *   <li>repositories live in infrastructure.persistence</li>
 *   <li>resource ownership port calls belong in application services, never in controllers</li>
 * </ul>
 */
@AnalyzeClasses(
        packages = "com.padelpro",
        importOptions = ImportOption.DoNotIncludeTests.class
)
class HexagonalArchitectureTest {

    // -------------------------------------------------------------------------
    // Rule 1 — Domain must NOT depend on infrastructure
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("com.padelpro.auth.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.padelpro.auth.infrastructure..")
                    .because("The domain layer must be free of infrastructure concerns " +
                             "(Ports & Adapters pattern).");

    // -------------------------------------------------------------------------
    // Rule 2 — Domain must NOT depend on application services
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule domain_should_not_depend_on_application_services =
            noClasses()
                    .that().resideInAPackage("com.padelpro.auth.domain..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.padelpro.auth.application.service..")
                    .because("Domain model must not know about application-layer orchestration.");

    // -------------------------------------------------------------------------
    // Rule 3 — Application services must NOT depend on infrastructure
    //
    // KNOWN VIOLATION (Wave 2 skeleton — Bug #TBD):
    //   AuthService and RegistrationService currently inject UserRepository
    //   (infrastructure.persistence) directly instead of going through a domain
    //   port interface (e.g. UserRepositoryPort in domain.port.out).
    //   Wave 3 must introduce the port interface and fix this DIP violation.
    //   This rule is intentionally kept RED to enforce the fix in Wave 3.
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("com.padelpro.auth.application.service..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.padelpro.auth.infrastructure..")
                    .because("Application services must only depend on ports (interfaces), " +
                             "not on concrete infrastructure adapters. " +
                             "Dependency inversion: infrastructure depends on application, not vice versa. " +
                             "Wave 2 skeleton violates this — Wave 3 must introduce UserRepositoryPort.");

    // -------------------------------------------------------------------------
    // Rule 4 — Controllers must reside in infrastructure.web
    //
    // Applies to all bounded contexts under com.padelpro (auth + usuarios + future).
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule controllers_should_be_in_infrastructure_web_package =
            classes()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage("..infrastructure.web..")
                    .because("All inbound HTTP adapters (controllers) belong in the " +
                             "infrastructure.web package of their respective bounded context.");

    // -------------------------------------------------------------------------
    // Rule 5 — Repositories must reside in infrastructure.persistence
    //
    // Applies to all bounded contexts under com.padelpro (auth + usuarios + future).
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule repositories_should_be_in_infrastructure_persistence_package =
            classes()
                    .that().haveSimpleNameEndingWith("Repository")
                    .should().resideInAPackage("..infrastructure.persistence..")
                    .because("All outbound persistence adapters (repositories) belong in the " +
                             "infrastructure.persistence package of their respective bounded context.");

    // -------------------------------------------------------------------------
    // Rule 6 — ResourceOwnershipPort must NOT be used in controllers
    //
    // Ownership checks belong in application services, not in HTTP adapters.
    // -------------------------------------------------------------------------

    @ArchTest
    static final ArchRule resource_ownership_must_not_be_in_controllers =
            noClasses()
                    .that().resideInAPackage("..infrastructure.web..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..domain.port.in..")
                    .because("Resource ownership port calls belong in application services, " +
                             "never in controllers.");
}
