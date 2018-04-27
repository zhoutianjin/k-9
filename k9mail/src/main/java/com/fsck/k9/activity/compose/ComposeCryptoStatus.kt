package com.fsck.k9.activity.compose


import com.fsck.k9.activity.compose.RecipientMvpView.CryptoSpecialModeDisplayType
import com.fsck.k9.activity.compose.RecipientMvpView.CryptoStatusDisplayType
import com.fsck.k9.activity.compose.RecipientPresenter.CryptoMode
import com.fsck.k9.message.AutocryptStatusInteractor
import com.fsck.k9.message.AutocryptStatusInteractor.RecipientAutocryptStatus
import com.fsck.k9.view.RecipientSelectView.Recipient
import org.openintents.openpgp.OpenPgpApiManager
import org.openintents.openpgp.OpenPgpApiManager.OpenPgpProviderState

/** This is an immutable object which contains all relevant metadata entered
 * during email composition to apply cryptographic operations before sending
 * or saving as draft.
 */
data class ComposeCryptoStatus(private val openPgpProviderState: OpenPgpProviderState,
                               val openPgpKeyId: Long?,
                               val recipientAddresses: List<String>,
                               val isPgpInlineModeEnabled: Boolean,
                               val isSenderPreferEncryptMutual: Boolean,
                               val isReplyToEncrypted: Boolean,
                               val isEncryptSubject: Boolean,
                               private val cryptoMode: CryptoMode,
                               private val recipientAutocryptStatus: RecipientAutocryptStatus? = null) {

    constructor(openPgpProviderState: OpenPgpProviderState,
                openPgpKeyId: Long?,
                recipientAddresses: List<Recipient>,
                isPgpInlineModeEnabled: Boolean,
                isSenderPreferEncryptMutual: Boolean,
                isReplyToEncrypted: Boolean,
                isEncryptSubject: Boolean,
                cryptoMode: CryptoMode) : this(
            openPgpProviderState, openPgpKeyId,
            recipientAddresses.map { it.address.address },
            isPgpInlineModeEnabled, isSenderPreferEncryptMutual, isReplyToEncrypted, isEncryptSubject, cryptoMode)

    private val recipientAutocryptStatusType = recipientAutocryptStatus?.type
    private val isRecipientsPreferEncryptMutual = recipientAutocryptStatus?.type?.isMutual ?: false

    private val isExplicitlyEnabled = cryptoMode == CryptoMode.CHOICE_ENABLED
    private val isMutualAndNotDisabled = cryptoMode != CryptoMode.CHOICE_DISABLED && canEncryptAndIsMutualDefault()
    private val isReplyAndNotDisabled = cryptoMode != CryptoMode.CHOICE_DISABLED && isReplyToEncrypted

    val isOpenPgpConfigured = openPgpProviderState != OpenPgpProviderState.UNCONFIGURED

    val isSignOnly = cryptoMode == CryptoMode.SIGN_ONLY

    val isEncryptionEnabled = when {
        openPgpProviderState == OpenPgpProviderState.UNCONFIGURED -> false
        isSignOnly -> false
        isExplicitlyEnabled -> true
        isMutualAndNotDisabled -> true
        isReplyAndNotDisabled -> true
        else -> false
    }

    val isProviderStateOk = openPgpProviderState == OpenPgpProviderState.OK

    val isUserChoice = cryptoMode != CryptoMode.NO_CHOICE
    val isSigningEnabled = cryptoMode == CryptoMode.SIGN_ONLY || isEncryptionEnabled
    val recipientAddressesAsArray = recipientAddresses.toTypedArray()

    private val displayTypeFromProviderError = when (openPgpProviderState) {
        OpenPgpApiManager.OpenPgpProviderState.OK -> null
        OpenPgpApiManager.OpenPgpProviderState.UNCONFIGURED -> CryptoStatusDisplayType.UNCONFIGURED
        OpenPgpApiManager.OpenPgpProviderState.UNINITIALIZED -> CryptoStatusDisplayType.UNINITIALIZED
        OpenPgpApiManager.OpenPgpProviderState.ERROR, OpenPgpApiManager.OpenPgpProviderState.UI_REQUIRED -> CryptoStatusDisplayType.ERROR
    }

    private val displayTypeFromAutocryptError = when (recipientAutocryptStatusType) {
        null, AutocryptStatusInteractor.RecipientAutocryptStatusType.ERROR -> CryptoStatusDisplayType.ERROR
        else -> null
    }

    private val displayTypeFromEnabledAutocryptStatus = when {
        !isEncryptionEnabled -> null
        recipientAutocryptStatusType == null -> CryptoStatusDisplayType.ERROR
        !recipientAutocryptStatusType.canEncrypt() -> CryptoStatusDisplayType.ENABLED_ERROR
        recipientAutocryptStatusType.isConfirmed -> CryptoStatusDisplayType.ENABLED_TRUSTED
        else -> CryptoStatusDisplayType.ENABLED
    }

    private val displayTypeFromSignOnly = when {
        isSignOnly -> CryptoStatusDisplayType.SIGN_ONLY
        else -> null
    }

    private val displayTypeFromEncryptionAvailable = when {
        recipientAutocryptStatusType?.canEncrypt() == true -> CryptoStatusDisplayType.AVAILABLE
        else -> null
    }

    val displayType =
            displayTypeFromProviderError
                    ?: displayTypeFromAutocryptError
                    ?: displayTypeFromEnabledAutocryptStatus
                    ?: displayTypeFromSignOnly
                    ?: displayTypeFromEncryptionAvailable
                    ?: CryptoStatusDisplayType.UNAVAILABLE

    val specialModeDisplayType = when {
        openPgpProviderState != OpenPgpProviderState.OK -> CryptoSpecialModeDisplayType.NONE
        isSignOnly && isPgpInlineModeEnabled -> CryptoSpecialModeDisplayType.SIGN_ONLY_PGP_INLINE
        isSignOnly -> CryptoSpecialModeDisplayType.SIGN_ONLY
        allRecipientsCanEncrypt() && isPgpInlineModeEnabled -> CryptoSpecialModeDisplayType.PGP_INLINE
        else -> CryptoSpecialModeDisplayType.NONE
    }

    val autocryptPendingIntent = recipientAutocryptStatus?.intent

    val sendErrorStateOrNull = when {
        openPgpProviderState != OpenPgpProviderState.OK -> SendErrorState.PROVIDER_ERROR
        openPgpKeyId == null && (isEncryptionEnabled || isSignOnly) -> SendErrorState.KEY_CONFIG_ERROR
        isEncryptionEnabled && !allRecipientsCanEncrypt() -> SendErrorState.ENABLED_ERROR
        else -> null
    }

    val attachErrorStateOrNull = when {
        openPgpProviderState == OpenPgpProviderState.UNCONFIGURED -> null
        isPgpInlineModeEnabled -> AttachErrorState.IS_INLINE
        else -> null
    }

    fun allRecipientsCanEncrypt() = recipientAutocryptStatus?.type?.canEncrypt() == true

    fun canEncryptAndIsMutualDefault() = allRecipientsCanEncrypt() && isSenderPreferEncryptMutual && isRecipientsPreferEncryptMutual

    fun hasAutocryptPendingIntent() = recipientAutocryptStatus?.hasPendingIntent() == true

    fun hasRecipients(): Boolean {
        return recipientAddresses.isNotEmpty()
    }

    fun withRecipientAutocryptStatus(recipientAutocryptStatusType: RecipientAutocryptStatus) = ComposeCryptoStatus(
            openPgpProviderState = openPgpProviderState,
            cryptoMode = cryptoMode,
            recipientAddresses = recipientAddresses,
            isReplyToEncrypted = isReplyToEncrypted,
            openPgpKeyId = openPgpKeyId,
            isPgpInlineModeEnabled = isPgpInlineModeEnabled,
            isSenderPreferEncryptMutual = isSenderPreferEncryptMutual,
            isEncryptSubject = isEncryptSubject,
            recipientAutocryptStatus = recipientAutocryptStatusType
    )

    enum class SendErrorState {
        PROVIDER_ERROR,
        KEY_CONFIG_ERROR,
        ENABLED_ERROR
    }

    enum class AttachErrorState {
        IS_INLINE
    }

}
