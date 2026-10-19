package de.latzig.game.tob;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Layering rules of ToB 2026 (roadmap section 3). Phase 7d: the rules that name the converter's package live in the
 * converter's own test package, which is not published with the game.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.latzig.game.tob");

    @Test
    void coreIsEngineFree() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jme3..",
                        "com.simsilica..",
                        "de.latzig.game.tob.client..",
                        "de.latzig.game.tob.editor..")
                .check(CLASSES);
    }

    @Test
    void coreModelUsesOnlyJdk() {
        classes().that().resideInAPackage("de.latzig.game.tob.core.model..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "de.latzig.game.tob.core.model..")
                .check(CLASSES);
    }

    @Test
    void coreNoiseUsesOnlyJdk() {
        classes().that().resideInAPackage("de.latzig.game.tob.core.noise..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "de.latzig.game.tob.core.noise..")
                .check(CLASSES);
    }

    @Test
    void coreIoUsesOnlyModelSimProgressAndJackson() {
        classes().that().resideInAPackage("de.latzig.game.tob.core.io..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.fasterxml.jackson..",
                        "de.latzig.game.tob.core.model..", "de.latzig.game.tob.core.sim..",
                        "de.latzig.game.tob.core.progress..", "de.latzig.game.tob.core.io..")
                .check(CLASSES);
    }

    /** Phase 5a: the original's progression and score rules and the profile model, engine- and I/O-free. */
    @Test
    void coreProgressUsesOnlyJdkAndModel() {
        classes().that().resideInAPackage("de.latzig.game.tob.core.progress..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "de.latzig.game.tob.core.model..",
                        "de.latzig.game.tob.core.progress..")
                .check(CLASSES);
    }

    @Test
    void coreSimUsesOnlyModel() {
        classes().that().resideInAPackage("de.latzig.game.tob.core.sim..")
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "de.latzig.game.tob.core.model..",
                        "de.latzig.game.tob.core.sim..")
                .check(CLASSES);
    }

    @Test
    void coreSimIsHeadlessAndIoFree() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.core.sim..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jme3..",
                        "com.simsilica..",
                        "de.latzig.game.tob.client..",
                        "de.latzig.game.tob.editor..",
                        "de.latzig.game.tob.core.io..")
                .check(CLASSES);
    }

    @Test
    void clientDoesNotDependOnEditor() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.client..")
                .should().dependOnClassesThat().resideInAPackage("de.latzig.game.tob.editor..")
                .check(CLASSES);
    }

    @Test
    void clientLogicIsEngineFree() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.client.logic..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.jme3..",
                        "com.simsilica..",
                        "de.latzig.game.tob.client.view..",
                        "de.latzig.game.tob.client.input..")
                .check(CLASSES);
    }

    /** Phase 7a: the shared building blocks in {@code client.logic} itself know none of its feature packages. */
    @Test
    void sharedClientLogicUsesNoFeaturePackage() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.client.logic")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "de.latzig.game.tob.client.logic.effects..",
                        "de.latzig.game.tob.client.logic.audio..",
                        "de.latzig.game.tob.client.logic.game..",
                        "de.latzig.game.tob.client.logic.ui..",
                        "de.latzig.game.tob.client.logic.frontend..",
                        "de.latzig.game.tob.client.logic.editor..")
                .check(CLASSES);
    }

    /** Phase 7a: the effects and the sound decide from the world alone; they know no game, UI or screen logic. */
    @Test
    void effectsAndAudioKnowNoGameUiOrScreens() {
        noClasses().that().resideInAnyPackage("de.latzig.game.tob.client.logic.effects..",
                "de.latzig.game.tob.client.logic.audio..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "de.latzig.game.tob.client.logic.game..",
                        "de.latzig.game.tob.client.logic.ui..",
                        "de.latzig.game.tob.client.logic.frontend..",
                        "de.latzig.game.tob.client.logic.editor..")
                .check(CLASSES);
    }

    /** Phase 7a: the game and the UI building blocks know neither the start screens nor the Tower Designer. */
    @Test
    void gameAndUiKnowNoScreens() {
        noClasses().that().resideInAnyPackage("de.latzig.game.tob.client.logic.game..",
                "de.latzig.game.tob.client.logic.ui..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "de.latzig.game.tob.client.logic.frontend..",
                        "de.latzig.game.tob.client.logic.editor..")
                .check(CLASSES);
    }

    /** Phase 7a fix wave (F7): effects decide the world's look alone; the sound layer builds on them, not back. */
    @Test
    void effectsKnowNoAudio() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.client.logic.effects..")
                .should().dependOnClassesThat().resideInAPackage("de.latzig.game.tob.client.logic.audio..")
                .check(CLASSES);
    }

    /** Phase 7a fix wave (F7): the game building blocks know none of the start screens' UI drawing blocks. */
    @Test
    void gameKnowsNoUi() {
        noClasses().that().resideInAPackage("de.latzig.game.tob.client.logic.game..")
                .should().dependOnClassesThat().resideInAPackage("de.latzig.game.tob.client.logic.ui..")
                .check(CLASSES);
    }
}
