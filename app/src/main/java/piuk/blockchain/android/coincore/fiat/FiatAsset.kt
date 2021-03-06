package piuk.blockchain.android.coincore.fiat

import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.swap.nabu.datamanagers.CustodialWalletManager
import com.blockchain.swap.nabu.datamanagers.repositories.AssetBalancesRepository
import com.blockchain.swap.nabu.models.nabu.KycTierLevel
import com.blockchain.swap.nabu.service.TierService
import com.blockchain.wallet.DefaultLabels
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.Asset
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.FiatAccount
import piuk.blockchain.android.coincore.ReceiveAddress
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.androidcore.data.exchangerate.ExchangeRateDataManager

class FiatAsset(
    private val labels: DefaultLabels,
    private val assetBalancesRepository: AssetBalancesRepository,
    private val exchangeRateDataManager: ExchangeRateDataManager,
    private val custodialWalletManager: CustodialWalletManager,
    private val tierService: TierService,
    private val currencyPrefs: CurrencyPrefs
) : Asset {
    override fun init(): Completable = Completable.complete()
    override val isEnabled: Boolean = true

    override fun accountGroup(filter: AssetFilter): Maybe<AccountGroup> =
        when (filter) {
            AssetFilter.All,
            AssetFilter.Custodial -> fetchFiatWallets()
            AssetFilter.NonCustodial,
            AssetFilter.Interest -> TODO()
        }

    private fun fetchFiatWallets(): Maybe<AccountGroup> =
        tierService.tiers()
            .flatMap { tier ->
                custodialWalletManager.getSupportedFundsFiats(
                    currencyPrefs.selectedFiatCurrency,
                    tier.isApprovedFor(KycTierLevel.GOLD)
                )
            }.flatMapMaybe { fiatList ->
                if (fiatList.isNotEmpty()) {
                    Maybe.just(
                        FiatAccountGroup(
                            label = "Fiat Accounts",
                            accounts = fiatList.map { getAccount(it) }
                        )
                    )
                } else {
                    Maybe.empty<AccountGroup>()
                }
            }

    private val accounts = mutableMapOf<String, FiatAccount>()

    private fun getAccount(fiatCurrency: String): FiatAccount =
        accounts.getOrPut(fiatCurrency) {
            FiatCustodialAccount(
                label = labels.getDefaultCustodialFiatWalletLabel(fiatCurrency),
                fiatCurrency = fiatCurrency,
                assetBalancesRepository = assetBalancesRepository,
                exchangesRatesDataManager = exchangeRateDataManager,
                custodialWalletManager = custodialWalletManager
            )
        }

    override fun transactionTargets(account: SingleAccount): Single<SingleAccountList> =
        when (account) {
            is TradingAccount -> getFiatAccountList()
            else -> Single.just(emptyList())
        }

    private fun getFiatAccountList(): Single<SingleAccountList> =
        fetchFiatWallets().map {
            it.accounts
        }.toSingle(emptyList())

    override fun parseAddress(address: String): Maybe<ReceiveAddress> = Maybe.empty()
}
