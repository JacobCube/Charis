package org.talosware.charis.ui.my_account.connections

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.talosware.charis.ui.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import org.talosware.charis.R
import org.talosware.charis.api.to.UserConnection
import org.talosware.charis.api.to.UserConnectionStatus
import org.talosware.charis.api.to.UserConnectionType
import org.talosware.charis.databinding.FragmentConnectionsRequestsLayoutBinding
import org.talosware.charis.ui.base.ShimmerRecyclerFragment
import org.talosware.charis.ui.components.recycler.x_recycler_view.XRecyclerView
import org.talosware.charis.ui.my_account.connections.adapter.RecyclerConnectionRequestListAdapter
import org.talosware.charis.utils.GeneralDataManager
import org.talosware.charis.utils.NavigationUtils
import org.talosware.charis.utils.Tools.replaceUserConnection
import org.talosware.charis.utils.ViewUtils

@AndroidEntryPoint
class ConnectionsRequestsFragment: ShimmerRecyclerFragment<ConnectionsViewModel, UserConnection, FragmentConnectionsRequestsLayoutBinding>(
        ConnectionsViewModel::class.java,
        null
    ) {
    override var navIconType: BaseActivity.NavIconType?
            = BaseActivity.NavIconType.BACK

    override var bottomBarType: BaseActivity.BottomBarType?
            = BaseActivity.BottomBarType.NONE

    override var headerType: BaseActivity.HeaderType?
            = BaseActivity.HeaderType.SIMPLE

    override val refreshData: Boolean = false

    override val loadMore: Boolean = false

    override var recyclerViewId: Int = R.id.recycler_connections_friends

    override var shimmerItemLayout: Int = R.layout.item_friends_shimmer_layout

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentConnectionsRequestsLayoutBinding {
        return FragmentConnectionsRequestsLayoutBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onDataRequest(false)

        observe(GeneralDataManager.userConnections) { requests ->
            requests.filter {
                it.targetDisplayName == GeneralDataManager.username
                        && it.connectionType == UserConnectionType.FRIEND
                        && it.status == UserConnectionStatus.WAITING
            }.sortedByDescending { it.creationTime }.let {
                hideException()
                onDataReceived(it)
            }
        }

        observe(viewModel.dataManager.sentConnectionRequest) {
            if(it != null) {
                baseActivity?.showSnackbar(
                    text = getString(R.string.connections_add_new_success),
                    snackbarType = ViewUtils.SnackbarType.SUCCESS
                )
                GeneralDataManager.userConnections.replaceUserConnection(it)
            }else {
                baseActivity?.showSnackbar(
                    text = getString(R.string.connections_add_new_failure),
                    snackbarType = ViewUtils.SnackbarType.ERROR
                )
            }
        }
    }

    override fun onItemClick(view: View?, position: Int) {
        super.onItemClick(view, position)
        navigateToUser(position)
    }

    /** navigates to clicked user profile */
    private fun navigateToUser(position: Int) {
        GeneralDataManager.userConnections.value?.filter {
            it.targetDisplayName == GeneralDataManager.username
                    && it.connectionType == UserConnectionType.FRIEND
                    && it.status == UserConnectionStatus.WAITING
        }?.sortedByDescending { it.creationTime }
            ?.getOrNull(position)
            ?.getUserInformation()?.let {
                NavigationUtils.navigateToUserDetail(
                    baseActivity,
                    it.uid,
                    it.blueberries ?: 0,
                    it.displayName,
                    it.profilePictureId
                )
            }
    }

    override fun onDataRequest(isSpecial: Boolean) {
        super.onDataRequest(isSpecial)
    }

    override fun getRecyclerAdapter(data: List<UserConnection>): XRecyclerView.WrapAdapter<*, *> {
        return RecyclerConnectionRequestListAdapter(
            baseActivity,
            data.toMutableList(),
            recyclerView
        ).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    override fun onAdapterRefresh(data: List<UserConnection>) {
        (recyclerView.adapter as? RecyclerConnectionRequestListAdapter)
            ?.refreshItems(data)
    }

    override fun showEmptyView() {
        showException(
            R.drawable.ill_empty,
            getString(R.string.connections_requests_exception_empty_title),
            getString(R.string.connections_requests_exception_empty_description, GeneralDataManager.username),
            getString(R.string.connections_requests_exception_empty_action),
            {
                NavigationUtils.navigateToMyGifts(baseActivity)
            },
            viewGroup = safeBinding?.root?.findViewById(R.id.container_connections_requests)
        )
    }
}