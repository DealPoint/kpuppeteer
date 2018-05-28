package io.dealpoint.kpuppeteer

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable

// see https://circleci.com/docs/1.0/dont-run/

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Tag("NoCi")
@DisabledIfEnvironmentVariable(named = "CI", matches = ".*")
annotation class NoCi
