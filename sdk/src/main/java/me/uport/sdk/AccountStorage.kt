package me.uport.sdk

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.uport.sdk.identity.Account
import me.uport.sdk.identity.AccountType
import me.uport.sdk.identity.HDAccount
import me.uport.sdk.identity.MetaIdentityAccount

interface AccountStorage {
    fun upsert(newAcc: Account)

    fun get(handle: String): Account?

    fun delete(handle: String)

    fun all(): List<Account?>

    fun upsertAll(list: Collection<Account>)
}


/**
 * An account storage mechanism that relies on [SharedPreferences] for persistence to disk
 *
 * Accounts are serialized then wrapped in an AccountHolder then along with the AccountType and isDefault
 *
 * Accounts are loaded during construction and then relayed from memory
 */
class SharedPrefsAccountStorage(
        private val prefs: SharedPreferences
) : AccountStorage {

    private val accounts = mapOf<String, AccountHolder>().toMutableMap()

    init {
        prefs.getStringSet(KEY_ACCOUNTS, emptySet())
                .orEmpty()
                .forEach { serialized ->
                    val acc = try {
                        HDAccount.fromJson(serialized)
                    } catch (ex: Exception) {
                        null
                    }

                    acc?.let { upsert(it) }
                }
    }


    override fun upsert(newAcc: Account) {
        accounts[newAcc.handle] = buildAccountHolder(newAcc)
        persist()
    }

    override fun upsertAll(list: Collection<Account>) {
        list.forEach {
            accounts[it.handle] = buildAccountHolder(it)
        }

        persist()
    }

    override fun get(handle: String): Account? = fetchAccountFromHolder(accounts[handle])

    override fun delete(handle: String) {
        accounts.remove(handle)
        persist()
    }

    override fun all(): List<Account?> = fetchAllAccounts()

    private fun persist() {
        prefs.edit()
                .putStringSet(KEY_ACCOUNTS, accounts.values.map { it.toJson() }.toSet())
                .apply()
    }

    companion object {
        private const val KEY_ACCOUNTS = "accounts"
    }

    private fun buildAccountHolder(account: Account,
                                   isDefault: Boolean = false): AccountHolder {

        val acc = when (account.type) {
            AccountType.HDKeyPair -> (account as HDAccount).toJson()
            AccountType.MetaIdentityManager -> (account as MetaIdentityAccount).toJson()
            else -> throw IllegalArgumentException("Storage not supported AccountType ${account.type}")
        }
        return AccountHolder(acc, isDefault, account.type)
    }

    private fun fetchAccountFromHolder(accountHolder: AccountHolder?): Account? {
        return when (accountHolder?.type) {
            AccountType.HDKeyPair -> HDAccount.fromJson(accountHolder.account)
            AccountType.MetaIdentityManager -> MetaIdentityAccount.fromJson(accountHolder.account)
            else -> return null
        }
    }

    private fun fetchAllAccounts(): List<Account?> {
        val listOfAccounts = mutableListOf<Account?>()

        accounts.forEach {
            listOfAccounts.add(fetchAccountFromHolder(it.value))
        }

        return listOfAccounts.toList()
    }
}


/**
 * Used to wrap any type of account before it is stored
 */
@Serializable
data class AccountHolder(
        val account: String,
        private val isDefault: Boolean = false,
        val type: AccountType?
) {

    /**
     * serializes accountHolder
     */
    fun toJson(pretty: Boolean = false): String = if (pretty) Json.indented.stringify(AccountHolder.serializer(), this) else Json.stringify(AccountHolder.serializer(), this)

    companion object {

        val blank = AccountHolder("", false, null)

        /**
         * de-serializes accountHolder
         */
        fun fromJson(serializedAccount: String?): AccountHolder? {
            if (serializedAccount == null || serializedAccount.isEmpty()) {
                return null
            }

            return Json.parse(AccountHolder.serializer(), serializedAccount)
        }
    }
}