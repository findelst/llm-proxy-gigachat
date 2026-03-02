package ru.ddd.llmproxy.unit

/**
 * Base configuration class for unit tests.
 *
 * This class provides common test setup and utilities for unit tests.
 * Unit tests should not use Spring Boot test context - they test individual components in isolation.
 */
abstract class BaseTestConfig {

    /**
     * Helper method to create a test string with specific length.
     */
    protected fun createTestString(length: Int, char: Char = 'a'): String {
        return CharArray(length) { char }.concatToString()
    }

    /**
     * Helper method to get current timestamp in milliseconds.
     */
    protected fun now(): Long = System.currentTimeMillis()

    /**
     * Helper method to create a unique test identifier.
     */
    protected fun uniqueId(): String = "test-${now()}"
}
