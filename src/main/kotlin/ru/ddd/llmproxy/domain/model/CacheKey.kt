package ru.ddd.llmproxy.domain.model

/**
 * Value object representing a deterministic cache key.
 *
 * @param hash SHA-256 hex digest of the request
 * @param providerUrl Base URL of the provider for cache isolation
 */
data class CacheKey(
    val hash: String,
    val providerUrl: String
) {
    init {
        require(hash.length == 64) { "Hash must be 64 characters (SHA-256 hex)" }
        require(hash.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Hash must be a valid hex string"
        }
        require(providerUrl.isNotBlank()) { "Provider URL must not be blank" }
    }

    /**
     * Returns a composite key string for storage.
     */
    fun toStorageKey(): String = "$providerUrl:$hash"

    companion object {
        /**
         * Creates a CacheKey from a storage key.
         */
        fun fromStorageKey(storageKey: String): CacheKey {
            val parts = storageKey.split(":", limit = 2)
            require(parts.size == 2) { "Invalid storage key format" }
            return CacheKey(hash = parts[1], providerUrl = parts[0])
        }
    }
}
