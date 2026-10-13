package com.jamesdpeters.chestsplusplus;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.jamesdpeters.chestsplusplus.core.Services;
import com.jamesdpeters.chestsplusplus.testing.Tags;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchIgnore;
import com.tngtech.archunit.junit.ArchTag;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.logging.Logger;

/**
 * The AGENTS.md rules that bytecode can show. The banned Lombok annotations are source-only, so {@code lombok.config} rejects those
 * instead.
 */
@ArchTag(Tags.UNIT)
@AnalyzeClasses(packages = "com.jamesdpeters.chestsplusplus", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest static final ArchRule linkKnowsNoFeature = noClasses().that().resideInAPackage("com.jamesdpeters.chestsplusplus.link..").should()
            .dependOnClassesThat().resideInAnyPackage("com.jamesdpeters.chestsplusplus.chestlink..", "com.jamesdpeters.chestsplusplus.autocraft..")
            .because("chestlink and autocraft build on link, never the other way round");

    @ArchTest static final ArchRule displayKnowsNoGroupType = noClasses().that().resideInAPackage("com.jamesdpeters.chestsplusplus.display..")
            .should().dependOnClassesThat().resideInAnyPackage("com.jamesdpeters.chestsplusplus.link..",
                    "com.jamesdpeters.chestsplusplus.chestlink..", "com.jamesdpeters.chestsplusplus.autocraft..")
            .because("link builds on display, and each group type's handler gives displays their content through DisplayService.Content");

    /** The root package is left out: ChestsPlusPlus wires every package together. */
    @ArchIgnore(reason = "core cycles with access, model, message and persistence: Services holds them all, and they use core's helpers")
    @ArchTest static final ArchRule packagesAreAcyclic = slices().matching("com.jamesdpeters.chestsplusplus.(**)").should().beFreeOfCycles();

    @ArchTest static final ArchRule onlyCommandsLookUpServices = noClasses().that()
            .resideOutsideOfPackage("com.jamesdpeters.chestsplusplus.command..")
            .and(not(belongToAnyOf(ChestsPlusPlus.class)))
            .should().callMethod(Services.class, "get", Class.class)
            .because("collaborators come in through the constructor; commands are built before any services exist");

    @ArchTest static final ArchRule noJulLoggers = noFields().should().haveRawType(Logger.class)
            .because("logging goes through @Slf4j(topic = ChestsPlusPlus.NAME)");

    @ArchTest static final ArchRule jdbiStaysInStores = noClasses().that().resideOutsideOfPackage("com.jamesdpeters.chestsplusplus.persistence..")
            .and(not(describe("belong to a *Store", ArchitectureTest::belongsToStore)))
            .should().dependOnClassesThat().resideInAPackage("org.jdbi..")
            .because("SQL lives in the store that owns the table");

    private static boolean belongsToStore(JavaClass type) {
        JavaClass outer = type;
        while (outer.getEnclosingClass().isPresent()) outer = outer.getEnclosingClass().get();
        return outer.getSimpleName().endsWith("Store");
    }
}
