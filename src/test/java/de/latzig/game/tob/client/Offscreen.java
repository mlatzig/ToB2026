package de.latzig.game.tob.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Phase 7d: marks a test class that renders in jME's offscreen surface (an invisible GLFW window) and so needs
 * OpenGL 3.2 - a graphics card and its driver. Its JUnit tag lets a machine without one leave these tests out with
 * {@code mvn verify -DexcludedGroups=offscreen}; the GitHub Actions runners have no graphics card. On the author's
 * machine they run with every build.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("offscreen")
@interface Offscreen {
}
