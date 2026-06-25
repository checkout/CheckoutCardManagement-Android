package com.checkout.cardmanagement.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class ProvisioningConfigurationTest {
    @Test
    fun `should expose constructor properties`() {
        val config = createConfig()

        assertEquals(ISSUER_ID, config.issuerID)
        assertEquals(SERVICE_URL, config.serviceURL)
        assertEquals(DIGITAL_CARD_URL, config.digitalCardURL)
        assertTrue(config.serviceRSAExponent.contentEquals(EXPONENT))
        assertTrue(config.serviceRSAModulus.contentEquals(MODULUS))
        assertEquals(VISA_CLIENT_APP_ID, config.visaClientAppId)
    }

    @Test
    fun `should expose null visaClientAppId when not provided`() {
        val config =
            ProvisioningConfiguration(
                issuerID = ISSUER_ID,
                serviceRSAExponent = EXPONENT.copyOf(),
                serviceRSAModulus = MODULUS.copyOf(),
                serviceURL = SERVICE_URL,
                digitalCardURL = DIGITAL_CARD_URL,
            )

        assertNull(config.visaClientAppId)
    }

    @Test
    fun `equals should return true for same instance`() {
        val config = createConfig()

        assertTrue(config == config)
    }

    @Test
    fun `equals should return true when content is the same`() {
        val config1 = createConfig()
        val config2 = createConfig()

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `equals should return false when issuerID differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(issuerID = "OTHER")

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return false when serviceRSAExponent differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(serviceRSAExponent = byteArrayOf(9, 9, 9))

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return false when serviceRSAModulus differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(serviceRSAModulus = byteArrayOf(9, 9, 9))

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return false when serviceURL differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(serviceURL = "OTHER")

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return false when digitalCardURL differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(digitalCardURL = "OTHER")

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return false when visaClientAppId differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(visaClientAppId = "OTHER")

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return false when visaClientAppId is null for one but not the other`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(visaClientAppId = null)

        assertNotEquals(config1, config2)
    }

    @Test
    fun `equals should return true when both have null visaClientAppId`() {
        val config1 = createConfig().copy(visaClientAppId = null)
        val config2 = createConfig().copy(visaClientAppId = null)

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `equals should return false against null`() {
        val config = createConfig()

        assertFalse(config.equals(null))
    }

    @Test
    fun `equals should return false against different type`() {
        val config = createConfig()

        assertFalse(config.equals("not a config"))
    }

    @Test
    fun `hashCode should be different when content differs`() {
        val config1 = createConfig()
        val config2 = createConfig().copy(issuerID = "OTHER")

        assertNotEquals(config1.hashCode(), config2.hashCode())
    }

    @Test
    fun `toNetworkConfig should produce a non null network ProvisioningConfiguration`() {
        val config = createConfig()

        val networkConfig = config.toNetworkConfig()

        assertNotNull(networkConfig)
    }

    @Test
    fun `toNetworkConfig should produce different network configs for different inputs`() {
        val networkConfig1 = createConfig().toNetworkConfig()
        val networkConfig2 = createConfig().copy(issuerID = "OTHER").toNetworkConfig()

        assertNotEquals(networkConfig1, networkConfig2)
    }

    @Test
    fun `toNetworkConfig should produce equal configs for equal inputs`() {
        val networkConfig1 = createConfig().toNetworkConfig()
        val networkConfig2 = createConfig().toNetworkConfig()

        assertEquals(networkConfig1, networkConfig2)
    }

    @Test
    fun `toNetworkConfig should produce different network configs when visaClientAppId differs`() {
        val networkConfig1 = createConfig().toNetworkConfig()
        val networkConfig2 = createConfig().copy(visaClientAppId = null).toNetworkConfig()

        assertNotEquals(networkConfig1, networkConfig2)
    }

    @Test
    fun `toNetworkConfig should produce equal network configs when both have null visaClientAppId`() {
        val networkConfig1 = createConfig().copy(visaClientAppId = null).toNetworkConfig()
        val networkConfig2 = createConfig().copy(visaClientAppId = null).toNetworkConfig()

        assertEquals(networkConfig1, networkConfig2)
    }

    private fun createConfig() =
        ProvisioningConfiguration(
            issuerID = ISSUER_ID,
            serviceRSAExponent = EXPONENT.copyOf(),
            serviceRSAModulus = MODULUS.copyOf(),
            serviceURL = SERVICE_URL,
            digitalCardURL = DIGITAL_CARD_URL,
            visaClientAppId = VISA_CLIENT_APP_ID,
        )

    private companion object {
        private const val ISSUER_ID = "ISSUER_ID"
        private const val SERVICE_URL = "SERVICE_URL"
        private const val DIGITAL_CARD_URL = "DIGITAL_CARD_URL"
        private const val VISA_CLIENT_APP_ID = "VISA_CLIENT_APP_ID"
        private val EXPONENT = byteArrayOf(1, 2, 3)
        private val MODULUS = byteArrayOf(4, 5, 6)
    }
}
