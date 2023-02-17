package org.talosware.charis.ui.gifts.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.DialogCallback
import com.afollestad.materialdialogs.MaterialDialog
import com.example.taloswarelib.update
import org.talosware.charis.R
import org.talosware.charis.ui.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.talosware.charis.api.GiftBasis
import org.talosware.charis.api.GiftStatus
import org.talosware.charis.api.GiftType
import org.talosware.charis.api.LoadingStatusType
import org.talosware.charis.api.to.Gift
import org.talosware.charis.databinding.FragmentGiftRecyclerLayoutBinding
import org.talosware.charis.notifications.NotificationSchedule
import org.talosware.charis.ui.components.recycler.x_recycler_view.XRecyclerView
import org.talosware.charis.ui.base.ShimmerRecyclerFragment
import org.talosware.charis.ui.gifts.GiftsFragment
import org.talosware.charis.ui.gifts.GiftsViewModel
import org.talosware.charis.ui.gifts.adapter.RecyclerAdapterGiftRequests
import org.talosware.charis.utils.DateUtils
import org.talosware.charis.utils.DateUtils.toCalendar
import org.talosware.charis.utils.GeneralDataManager
import org.talosware.charis.utils.ViewUtils
import java.util.*

@AndroidEntryPoint
class GiftRequestsFragment: ShimmerRecyclerFragment<GiftsViewModel, Gift, FragmentGiftRecyclerLayoutBinding>(
    GiftsViewModel::class.java,
    null
) {

    companion object {
        /** api call for response of accepting gift request */
        const val ACCEPT_API_KEY = "accept_api_key"
    }

    override var navIconType: BaseActivity.NavIconType?
            = BaseActivity.NavIconType.BACK

    override var bottomBarType: BaseActivity.BottomBarType?
            = BaseActivity.BottomBarType.NONE

    override val headerType: BaseActivity.HeaderType
            = BaseActivity.HeaderType.GIFTS

    override var shimmerItemLayout: Int = R.layout.item_gift_shimmer_layout

    override val loadMore: Boolean = false

    override val refreshData: Boolean = true

    /** current fragment listeners */
    private val filters = hashMapOf<GiftsFragment.GiftFilter, Pair<Any, ((gift: Gift) -> Boolean)>?>()

    /** filter listener */
    private val filterResponseListener = object: GiftsFragment.GiftsFilterResponse {
        override fun onTextFilterChanged(value: String?) {
            if(value != null) {
                filters[GiftsFragment.GiftFilter.TEXT] = value to { gift ->
                    gift.title.contains(value)
                            || gift.description.contains(value)
                            || gift.content.any { it.content.contains(value) || it.title.contains(value) }
                            || gift.senderDisplayName.contains(value)
                }
            }else {
                filters.remove(GiftsFragment.GiftFilter.TEXT)
            }
            applyFilters()
        }

        override fun onDateFilterSelected(value: Calendar?) {
            if(value != null) {
                filters[GiftsFragment.GiftFilter.DATE] = value to { gift ->
                    val from = gift.calculatedDates.firstOrNull()?.toCalendar()?.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                    }?.timeInMillis ?: 0

                    value.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 1)
                    }.timeInMillis in from..(gift.calculatedDates.lastOrNull()?.toCalendar()?.apply {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                    }?.timeInMillis ?: from.plus(120000))
                }
            }else {
                filters.remove(GiftsFragment.GiftFilter.DATE)
            }
            applyFilters()
        }

        override fun onTypeFilterSelected(value: GiftType?) {
            if(value != null) {
                filters[GiftsFragment.GiftFilter.TYPE] = value to { gift ->
                    gift.type == value
                }
            }else {
                filters.remove(GiftsFragment.GiftFilter.TYPE)
            }
            applyFilters()
        }

        override fun onBasisFilterSelected(value: GiftBasis?) {
            if(value != null) {
                filters[GiftsFragment.GiftFilter.BASIS] = value to { gift ->
                    gift.basis == value
                }
            }else {
                filters.remove(GiftsFragment.GiftFilter.BASIS)
            }
            applyFilters()
        }

        override fun onOnlyFriendsChanged(value: Boolean?) {

        }
    }

    /** applies all filters */
    private fun applyFilters() {
        if(originalData.isEmpty()) return
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                val list = mutableListOf<Gift>()
                list.addAll(originalData)
                Log.d("gift_requests", "originalData: $originalData, gifts: ${GeneralDataManager.gifts.value?.size}")
                val newList = list.filter {
                    (filters[GiftsFragment.GiftFilter.DATE] == null
                            || filters[GiftsFragment.GiftFilter.DATE]?.second?.invoke(it) == true)
                            && (filters[GiftsFragment.GiftFilter.TYPE] == null
                            || filters[GiftsFragment.GiftFilter.TYPE]?.second?.invoke(it) == true)
                            && (filters[GiftsFragment.GiftFilter.TEXT] == null
                            || filters[GiftsFragment.GiftFilter.TEXT]?.second?.invoke(it) == true)
                            && (filters[GiftsFragment.GiftFilter.BASIS] == null
                            || filters[GiftsFragment.GiftFilter.BASIS]?.second?.invoke(it) == true)
                }
                withContext(Dispatchers.Main) {
                    onDataReceived(newList)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? GiftsFragment)?.run {
            filterResponseListener = this@GiftRequestsFragment.filterResponseListener

            changeFilters(
                filters[GiftsFragment.GiftFilter.TEXT]?.first as? String,
                filters[GiftsFragment.GiftFilter.TYPE]?.first as? GiftType,
                filters[GiftsFragment.GiftFilter.BASIS]?.first as? GiftBasis,
                filters[GiftsFragment.GiftFilter.DATE]?.first as? Calendar
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onDataRequest(false)

        observe(GeneralDataManager.gifts) { result ->
            lifecycleScope.launch {
                val res = getFilteredGifts(result)
                Log.d("gift_requests", "filter res: $res")
                withContext(Dispatchers.Main) {
                    hideException()
                    onDataReceived(res)
                }
            }
        }

        observe(baseDataManager.currentLoading) { loading ->
            if(loading.key == ACCEPT_API_KEY && loading.status == LoadingStatusType.COMPLETED) {
                GeneralDataManager.gifts.value?.find { it.uid == loading.extraInformation }?.let {
                    it.getUserResponse(GeneralDataManager.user?.uid ?: "")?.let { response ->
                        if(response.status == GiftStatus.ACCEPTED) {
                            showSnackbar(
                                getString(R.string.gift_requests_successfully_accepted),
                                snackbarType = ViewUtils.SnackbarType.SUCCESS
                            )
                            NotificationSchedule.scheduleGiftNotifications(
                                baseContext,
                                it,
                                response.selectedHour,
                                response.selectedMinute
                            )
                        }
                    }
                }
            }
        }
    }

    /** returns filtered and sorted gifts */
    private suspend fun getFilteredGifts(value: List<Gift>): List<Gift> {
        return withContext(Dispatchers.Default) {
            value.filter {
                it.getUserResponse(GeneralDataManager.user?.uid ?: "")
                    ?.status == GiftStatus.PENDING
            }.sortedByDescending { it.creationTime }.onEach { gift ->
                gift.calculatedDates = gift.getDatesFromPatterns(
                    anchorDate = DateUtils.now().apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 1)
                    }
                ).filterNotNull()
            }
        }
    }

    override fun onRefresh() {
        recyclerView.refreshComplete()
    }

    override fun getRecyclerAdapter(data: List<Gift>): XRecyclerView.WrapAdapter<*, *> {
        return RecyclerAdapterGiftRequests(
            data.toMutableList(),
            recyclerView,
            object: RecyclerAdapterGiftRequests.RequestResponse {
                override fun onAccepted(uid: String, position: Int) {
                    val gift = GeneralDataManager.gifts.value?.find { it.uid == uid }
                    val minutes = arrayOfNulls<String>(60).mapIndexed { index, _ ->
                        if(index < 10) {
                            "0$index"
                        }else index.toString()
                    }
                    val hours = minutes.subList(0, 24).toTypedArray()

                    ViewUtils.makeNumberPickerDialog(
                        baseContext,
                        getString(R.string.gift_requests_accept_dialog_title),
                        getString(R.string.gift_requests_accept_dialog_description),
                        getString(R.string.gift_requests_accept_dialog_action),
                        { i1, i2 ->
                            gift?.run {
                                response[GeneralDataManager.user?.uid ?: ""]?.run {
                                    status = GiftStatus.ACCEPTED
                                    date = DateUtils.now().time
                                    selectedHour = i1.minus(1)
                                    selectedMinute = i2.minus(1)
                                }
                                //TODO refresh just one, this is bad UX
                                GeneralDataManager.gifts.update()
                            }
                            lifecycleScope.launch {
                                viewModel.answerGiftRequest(
                                    ACCEPT_API_KEY,
                                    uid,
                                    true,
                                    i1.minus(1),
                                    i2.minus(1)
                                )
                            }
                        },
                        getString(R.string.action_cancel),
                        object: DialogCallback {
                            override fun invoke(p1: MaterialDialog) {

                            }
                        },
                        gift?.notificationHour?.plus(1) ?: 0,
                        gift?.notificationMinutes?.plus(1) ?: 0,
                        hours,
                        minutes.toTypedArray(),
                        getString(R.string.gift_requests_accept_dialog_picker_hour),
                        getString(R.string.gift_requests_accept_dialog_picker_minutes)
                    )
                }

                override fun onRejected(uid: String, position: Int) {
                    ViewUtils.makeDialog(
                        baseContext,
                        getString(R.string.gift_requests_reject_dialog_title),
                        getString(R.string.gift_requests_reject_dialog_description),
                        getString(R.string.gift_requests_reject_dialog_action),
                        object: DialogCallback {
                            override fun invoke(p1: MaterialDialog) {
                                GeneralDataManager.gifts.value?.find { it.uid == uid }?.run {
                                    response[GeneralDataManager.user?.uid ?: ""]
                                        ?.status = GiftStatus.REJECTED
                                    //TODO refresh just one, this is bad UX
                                    GeneralDataManager.gifts.update()
                                }
                                lifecycleScope.launch {
                                    viewModel.answerGiftRequest(
                                        ACCEPT_API_KEY,
                                        uid,
                                        false,
                                        0,
                                        0
                                    )
                                }
                                GeneralDataManager.gifts.value?.removeIf { it.uid == uid }
                            }
                        },
                        getString(R.string.action_cancel),
                        object: DialogCallback {
                            override fun invoke(p1: MaterialDialog) {

                            }
                        }
                    )
                }
            }
        ).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    override fun onAdapterRefresh(data: List<Gift>) {
        (recyclerView.adapter as? RecyclerAdapterGiftRequests)?.refreshItems(data)
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentGiftRecyclerLayoutBinding {
        return FragmentGiftRecyclerLayoutBinding.inflate(inflater, container, false)
    }

    override fun showEmptyView() {
        if(filters.isNotEmpty()) {
            showException(
                R.drawable.ill_empty,
                getString(R.string.gift_exception_empty_filter_title),
                getString(R.string.gift_exception_empty_filter_description),
                getString(R.string.action_clear_filter),
                {
                    filters.clear()
                    applyFilters()
                    (parentFragment as? GiftsFragment)?.changeFilters()
                },
                viewGroup = safeBinding?.container
            )
        }else {
            showException(
                R.drawable.ill_empty,
                getString(R.string.gift_requests_exception_empty_title),
                getString(R.string.gift_requests_exception_empty_description),
                getString(R.string.gift_requests_exception_empty_action_primary),
                {
                    //TODO guide through events
                    showSnackbar(baseContext.getString(R.string.snackbar_todo))
                },
                viewGroup = safeBinding?.container
            )
        }
    }
}