package com.checkout.cardmanagement.model

/**
 * Configuration object used for Push Provisioning
 * Values should be shared in the Onboarding with Checkout
 * @param issuerID Issuer Identifier
 * @param serviceRSAExponent RSA Exponent in [ByteArray], from the key exchanged during onboarding
 * @param serviceRSAModulus RSA Modulus in [ByteArray], from the key exchanged during onboarding.
 * @param serviceURL URL String for the Service endpoint
 * @param digitalCardURL URL String for the Digital Service endpoint
 */
public data class ProvisioningConfiguration(
    internal val issuerID: String,
    internal val serviceRSAExponent: ByteArray,
    internal val serviceRSAModulus: ByteArray,
    internal val serviceURL: String,
    internal val digitalCardURL: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProvisioningConfiguration

        if (issuerID != other.issuerID) return false
        if (!serviceRSAExponent.contentEquals(other.serviceRSAExponent)) return false
        if (!serviceRSAModulus.contentEquals(other.serviceRSAModulus)) return false
        if (serviceURL != other.serviceURL) return false
        if (digitalCardURL != other.digitalCardURL) return false

        return true
    }

    override fun hashCode(): Int {
        var result = issuerID.hashCode()
        result = 31 * result + serviceRSAExponent.contentHashCode()
        result = 31 * result + serviceRSAModulus.contentHashCode()
        result = 31 * result + serviceURL.hashCode()
        result = 31 * result + digitalCardURL.hashCode()
        return result
    }

    internal fun toNetworkConfig() =
        com.checkout.cardnetwork.data.core.ProvisioningConfiguration(
            issuerID = this.issuerID,
            serviceRSAExponent = this.serviceRSAExponent,
            serviceRSAModulus = this.serviceRSAModulus,
            serviceURL = this.serviceURL,
            digitalCardURL = this.digitalCardURL,
        )
}
