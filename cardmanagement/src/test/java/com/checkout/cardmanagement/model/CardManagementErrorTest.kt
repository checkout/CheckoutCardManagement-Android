package com.checkout.cardmanagement.model

import com.checkout.cardnetwork.common.model.CardNetworkError
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType.CANCELLED
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType.CONFIGURATION_FAILURE
import com.checkout.cardnetwork.common.model.CardNetworkError.PushProvisioningFailureType.OPERATION_FAILURE
import org.junit.Assert.assertEquals
import org.junit.Test

internal class CardManagementErrorTest {
	@Test
	fun `AuthenticationFailure#toCardManagementError should map to AuthenticationFailure`() {
		assertEquals(
			CardManagementError.AuthenticationFailure,
			CardNetworkError.AuthenticationFailure.toCardManagementError()
		)
	}

	@Test
	fun `InvalidRequest#toCardManagementError should map to ConfigurationIssue(hint)`() {
		assertEquals(
			CardManagementError.ConfigurationIssue("HINT"),
			CardNetworkError.InvalidRequest("HINT").toCardManagementError()
		)
	}

	@Test
	fun `Misconfigured#toCardManagementError should map to ConfigurationIssue(hint)`() {
		assertEquals(
			CardManagementError.ConfigurationIssue("HINT"),
			CardNetworkError.Misconfigured("HINT").toCardManagementError()
		)
	}

	@Test
	fun `ServerIssue#toCardManagementError should map to ConnectionIssue`() {
		assertEquals(
			CardManagementError.ConnectionIssue,
			CardNetworkError.ServerIssue.toCardManagementError()
		)
	}

	@Test
	fun `Unauthenticated#toCardManagementError should map to Unauthenticated`() {
		assertEquals(
			CardManagementError.Unauthenticated,
			CardNetworkError.Unauthenticated.toCardManagementError()
		)
	}

	@Test
	fun `SecureOperationsFailure#toCardManagementError should map to UnableToPerformSecureOperation`() {
		assertEquals(
			CardManagementError.UnableToPerformSecureOperation,
			CardNetworkError.SecureOperationsFailure.toCardManagementError()
		)
	}

	@Test
	fun `PushProvisioningFailure#toCardManagementError should map to PushProvisioningFailure`() {
		listOf(
			CardManagementError.PushProvisioningFailureType.CANCELLED to CANCELLED,
			CardManagementError.PushProvisioningFailureType.OPERATION_FAILURE to OPERATION_FAILURE,
			CardManagementError.PushProvisioningFailureType.CONFIGURATION_FAILURE to CONFIGURATION_FAILURE
		).forEach { (managementErrorType, networkErrorType) ->
			assertEquals(
				CardManagementError.PushProvisioningFailure(managementErrorType),
				CardNetworkError.PushProvisioningFailure(networkErrorType).toCardManagementError()
			)
		}
	}

	@Test
	fun `ParsingFailure#toCardManagementError should map to ConnectionIssue`() {
		assertEquals(
			CardManagementError.ConnectionIssue,
			CardNetworkError.ParsingFailure.toCardManagementError()
		)
	}
}
