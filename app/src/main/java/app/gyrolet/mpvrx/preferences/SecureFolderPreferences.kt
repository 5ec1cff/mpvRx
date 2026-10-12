/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.preferences

import app.gyrolet.mpvrx.preferences.preference.PreferenceStore
import app.gyrolet.mpvrx.utils.security.PinHasher

/**
 * Preferences backing the Secure Folder feature: PIN gate, forgot-PIN security question,
 * whether the settings entry point is hidden from Preferences, and per-action "don't ask again"
 * flags for the confirm dialogs (Step 4).
 *
 * Only salted hashes are ever persisted — never the raw PIN or the raw answer text.
 */
class SecureFolderPreferences(
  preferenceStore: PreferenceStore,
) {
  companion object {
    const val DEFAULT_PIN_LENGTH = 4

    /** PIN lengths the user can pick when creating a PIN. */
    val SUPPORTED_PIN_LENGTHS = listOf(4, 6)
  }

  // ============================================================================
  // PIN
  // ============================================================================
  private val pinHash = preferenceStore.getString("secure_folder_pin_hash", "")
  private val pinSalt = preferenceStore.getString("secure_folder_pin_salt", "")

  /** Length of the stored PIN. Defaults to 4 so PINs created before 6-digit support keep working. */
  val pinLength = preferenceStore.getInt("secure_folder_pin_length", DEFAULT_PIN_LENGTH)

  fun isPinSet(): Boolean = pinHash.get().isNotBlank()

  /** Hashes and stores a new PIN, replacing any existing one. */
  fun setPin(pin: String) {
    val salt = PinHasher.generateSalt()
    pinSalt.set(salt)
    pinHash.set(PinHasher.hash(pin, salt))
    pinLength.set(pin.length)
  }

  fun verifyPin(pin: String): Boolean = PinHasher.verify(pin, pinSalt.get(), pinHash.get())

  // ============================================================================
  // Forgot-PIN security question
  // ============================================================================
  val securityQuestion = preferenceStore.getString("secure_folder_security_question", "")
  private val securityAnswerHash = preferenceStore.getString("secure_folder_security_answer_hash", "")
  private val securityAnswerSalt = preferenceStore.getString("secure_folder_security_answer_salt", "")

  fun setSecurityQuestion(
    question: String,
    answer: String,
  ) {
    val salt = PinHasher.generateSalt()
    securityQuestion.set(question)
    securityAnswerSalt.set(salt)
    securityAnswerHash.set(PinHasher.hash(normalizeAnswer(answer), salt))
  }

  fun verifySecurityAnswer(answer: String): Boolean =
    PinHasher.verify(normalizeAnswer(answer), securityAnswerSalt.get(), securityAnswerHash.get())

  private fun normalizeAnswer(answer: String): String = answer.trim().lowercase()

  // ============================================================================
  // Visibility of the Secure Folder entry point
  // ============================================================================

  /**
   * When true, the "Secure Folder Settings" row is hidden from the Preferences screen. The settings stay
   * reachable via the settings icon on the Secure Folder PIN gate (opened by double-tapping a header).
   */
  val isEntryPointHidden = preferenceStore.getBoolean("secure_folder_entry_hidden", false)

  // ============================================================================
  // Biometric authentication
  // ============================================================================
  val isBiometricEnabled = preferenceStore.getBoolean("secure_folder_biometric_enabled", false)

  // ============================================================================
  // Don't-ask-again flags for SecureConfirmDialog (Step 4)
  // ============================================================================
  val dontAskBeforeMove = preferenceStore.getBoolean("secure_folder_dont_ask_move", false)
  val dontAskBeforeRestore = preferenceStore.getBoolean("secure_folder_dont_ask_restore", false)
  val dontAskBeforeDelete = preferenceStore.getBoolean("secure_folder_dont_ask_delete", false)

  /** Wipes the PIN, security question and every Secure Folder flag back to first-run defaults. */
  fun resetAll() {
    pinHash.set("")
    pinSalt.set("")
    pinLength.set(DEFAULT_PIN_LENGTH)
    securityQuestion.set("")
    securityAnswerHash.set("")
    securityAnswerSalt.set("")
    isBiometricEnabled.set(false)
    isEntryPointHidden.set(false)
    dontAskBeforeMove.set(false)
    dontAskBeforeRestore.set(false)
    dontAskBeforeDelete.set(false)
  }
}
