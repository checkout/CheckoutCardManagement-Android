package com.checkout.cardmanagement.model

import com.checkout.cardmanagement.model.CardState.ACTIVE
import com.checkout.cardmanagement.model.CardState.INACTIVE
import com.checkout.cardmanagement.model.CardState.REVOKED
import com.checkout.cardmanagement.model.CardState.SUSPENDED
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class CardStateTest {
	@Test
	fun `SUSPENDED card should be able to be activated`() {
		assertTrue(SUSPENDED.getPossibleStateChanges().contains(ACTIVE))
	}

	@Test
	fun `SUSPENDED card should be able to be revoked`() {
		assertTrue(SUSPENDED.getPossibleStateChanges().contains(REVOKED))
	}

	@Test
	fun `SUSPENDED card should not be able to be suspended`() {
		assertFalse(SUSPENDED.getPossibleStateChanges().contains(SUSPENDED))
	}

	@Test
	fun `INACTIVE card should be able to be activated`() {
		assertTrue(INACTIVE.getPossibleStateChanges().contains(ACTIVE))
	}

	@Test
	fun `INACTIVE card should be able to be revoked`() {
		assertTrue(INACTIVE.getPossibleStateChanges().contains(REVOKED))
	}

	@Test
	fun `INACTIVE card should not be able to be suspended`() {
		assertFalse(INACTIVE.getPossibleStateChanges().contains(INACTIVE))
	}

	@Test
	fun `ACTIVE card should be able to be suspended`() {
		assertTrue(ACTIVE.getPossibleStateChanges().contains(SUSPENDED))
	}

	@Test
	fun `ACTIVE card should be able to be revoked`() {
		assertTrue(ACTIVE.getPossibleStateChanges().contains(REVOKED))
	}

	@Test
	fun `ACTIVE card should not be able to be activated`() {
		assertFalse(ACTIVE.getPossibleStateChanges().contains(ACTIVE))
	}

	@Test
	fun `REVOKED card should not be able to do anything`() {
		assertFalse(REVOKED.getPossibleStateChanges().contains(ACTIVE))
		assertFalse(REVOKED.getPossibleStateChanges().contains(INACTIVE))
		assertFalse(REVOKED.getPossibleStateChanges().contains(SUSPENDED))
		assertFalse(REVOKED.getPossibleStateChanges().contains(REVOKED))
	}
}
