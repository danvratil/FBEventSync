/*
    Copyright (C) 2018  Daniel Vrátil <me@dvratil.cz>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package cz.dvratil.fbeventsync

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar


class AccountAdapter(private var mContext: Context) : RecyclerView.Adapter<AccountAdapter.ViewHolder>()
                                                    , OnAccountsUpdateListener {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var name = view.findViewById<TextView>(R.id.account_card_name)
        var syncBtn = view.findViewById<Button>(R.id.account_card_sync_btn)
        var removeBtn = view.findViewById<Button>(R.id.account_card_remove_btn)
        var syncIndicator = view.findViewById<ProgressBar>(R.id.account_card_sync_progress)
        var avatarView  = view.findViewById<ImageView>(R.id.account_card_avatar)
    }

    data class AccountData(var account: Account, var isSyncing: Boolean) {
        fun id() = account.name.hashCode().toLong()
    }

    private val mAccountManager = AccountManager.get(mContext)
    private var mAccounts: MutableList<AccountData>

    init {
        setHasStableIds(true)

        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE) {
            // FIXME: Is this safe?
            (mContext as Activity).runOnUiThread(Runnable { checkSyncStatus() })
        }

        mAccounts = mutableListOf()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.account_card, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val account = mAccounts[position]
        holder.name.text = account.account.name
        holder.syncBtn.isEnabled = !account.isSyncing
        holder.syncIndicator.visibility = if (account.isSyncing) View.VISIBLE else View.GONE
        AvatarProvider.getAvatar(mContext, account.account, {
            holder.avatarView.setImageDrawable(it)
        })

        holder.syncBtn.setOnClickListener {
            CalendarSyncAdapter.requestSync(mContext, account.account)
        }
        holder.removeBtn.setOnClickListener{
            Authenticator.removeAccount(mContext, account.account)
        }
    }

    override fun getItemCount(): Int = mAccounts.count()

    // OnAccountUpdateListener interface
    override fun onAccountsUpdated(accounts: Array<out Account>) {
        val accountType = mContext.getString(R.string.account_type)
        mAccounts.clear()
        for (account in accounts.filter { it.type == accountType }) {
            mAccounts.add(AccountData(account, ContentResolver.isSyncActive(account, CalendarContract.AUTHORITY)))
        }
        notifyDataSetChanged()
    }

    private fun checkSyncStatus() {
        for (i in 0..(mAccounts.count() - 1)) {
            val syncing = ContentResolver.isSyncActive(mAccounts[i].account, CalendarContract.AUTHORITY)
            if (mAccounts[i].isSyncing != syncing) {
                mAccounts[i].isSyncing = syncing
                notifyItemChanged(i)
            }
        }
    }

    override fun getItemId(position: Int): Long = mAccounts[position].id()
}
