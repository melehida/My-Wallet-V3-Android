package piuk.blockchain.android.coincore.erc20.dgld

import com.blockchain.annotations.CommonCode
import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.WalletStatus
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.swap.nabu.datamanagers.EligibilityProvider
import com.blockchain.swap.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.CryptoCurrency
import info.blockchain.wallet.util.FormatsUtil
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.android.coincore.AddressParseError
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.erc20.Erc20Address
import piuk.blockchain.android.coincore.erc20.Erc20TokensBase
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.data.erc20.Erc20Account
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateService
import piuk.blockchain.androidcore.data.fees.FeeDataManager
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import java.util.concurrent.atomic.AtomicBoolean

internal class DgldAsset(
    payloadManager: PayloadDataManager,
    dgldAccount: Erc20Account,
    feeDataManager: FeeDataManager,
    custodialManager: CustodialWalletManager,
    exchangeRates: ExchangeRateDataManager,
    historicRates: ExchangeRateService,
    currencyPrefs: CurrencyPrefs,
    labels: DefaultLabels,
    pitLinking: PitLinking,
    crashLogger: CrashLogger,
    tiersService: TierService,
    environmentConfig: EnvironmentConfig,
    eligibilityProvider: EligibilityProvider,
    private val walletPreferences: WalletStatus,
    private val wDgldFeatureFlag: FeatureFlag
) : Erc20TokensBase(
    payloadManager,
    dgldAccount,
    feeDataManager,
    custodialManager,
    exchangeRates,
    historicRates,
    currencyPrefs,
    labels,
    pitLinking,
    crashLogger,
    tiersService,
    environmentConfig,
    eligibilityProvider
) {

    private val isDgldFeatureFlagEnabled = AtomicBoolean(false)

    override fun initToken(): Completable {
        return wDgldFeatureFlag.enabled.doOnSuccess {
            isDgldFeatureFlagEnabled.set(it)
        }.flatMapCompletable {
            super.initToken()
        }
    }

    override val isEnabled: Boolean
        get() = isDgldFeatureFlagEnabled.get()

    override val asset = CryptoCurrency.DGLD

    override fun loadNonCustodialAccounts(labels: DefaultLabels): Single<SingleAccountList> =
        Single.just(listOf(getNonCustodialDgldAccount()))

    private fun getNonCustodialDgldAccount(): CryptoAccount {
        val dgldAddress = erc20Account.ethDataManager.getEthWallet()?.account?.address
            ?: throw Exception("No ether wallet found")

        return DgldCryptoWalletAccount(
            payloadManager,
            labels.getDefaultNonCustodialWalletLabel(CryptoCurrency.DGLD),
            dgldAddress,
            erc20Account,
            feeDataManager,
            exchangeRates,
            walletPreferences,
            custodialManager
        )
    }

    @CommonCode("Exists in EthAsset and UsdtAsset")
    override fun parseAddress(address: String): Maybe<ReceiveAddress> =
        Single.just(isValidAddress(address)).flatMapMaybe { isValid ->
            if (isValid) {
                erc20Account.ethDataManager.isContractAddress(address).flatMapMaybe { isContract ->
                    if (isContract) {
                        throw AddressParseError(AddressParseError.Error.ETH_UNEXPECTED_CONTRACT_ADDRESS)
                    } else {
                        Maybe.just(DgldAddress(address))
                    }
                }
            } else {
                Maybe.empty<ReceiveAddress>()
            }
        }

    private fun isValidAddress(address: String): Boolean =
        FormatsUtil.isValidEthereumAddress(address)
}

internal class DgldAddress(
    address: String,
    label: String = address
) : Erc20Address(CryptoCurrency.DGLD, address, label)