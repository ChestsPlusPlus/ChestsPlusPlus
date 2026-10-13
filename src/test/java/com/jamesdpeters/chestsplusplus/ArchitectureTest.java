package com.jamesdpeters.chestsplusplus;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.jamesdpeters.chestsplusplus.model.StorageGroup;
import com.jamesdpeters.chestsplusplus.persistence.GroupStore;
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
    @ArchIgnore(reason = "core cycles with access, model and message: Services holds them all, and they use core's helpers")
    @ArchTest static final ArchRule packagesAreAcyclic = slices().matching("com.jamesdpeters.chestsplusplus.(**)").should().beFreeOfCycles();

    @ArchTest static final ArchRule onlyTheRootAndCommandsSeeComponents = noClasses().that()
            .resideOutsideOfPackages("com.jamesdpeters.chestsplusplus", "com.jamesdpeters.chestsplusplus.command..")
            .should().dependOnClassesThat().belongToAnyOf(Components.class, Features.class, Storage.class, Migration.class)
            .because("collaborators come in through the constructor; commands are built at bootstrap, before enable");

    @ArchTest static final ArchRule modelKnowsNoListener = noClasses().that().resideInAPackage("com.jamesdpeters.chestsplusplus.model..")
            .should().dependOnClassesThat().resideInAnyPackage("com.jamesdpeters.chestsplusplus.persistence..",
                    "com.jamesdpeters.chestsplusplus.display..", "com.jamesdpeters.chestsplusplus.migration..")
            .because("stores, displays and the migration hear of model changes through the listeners ChestsPlusPlus adds");

    @ArchTest static final ArchRule groupChangesGoThroughTheRegistry = noClasses().that()
            .resideOutsideOfPackages("com.jamesdpeters.chestsplusplus", "com.jamesdpeters.chestsplusplus.persistence..")
            .should().callMethod(Storage.class, "groupStore")
            .orShould().callMethod(GroupStore.class, "markDirty", StorageGroup.class)
            .because("groups.changed(group) saves and redisplays a group; GroupStore listens to the registry");

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
