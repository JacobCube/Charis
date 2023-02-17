package org.talosware.charis.ui.gifts.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import org.talosware.charis.R
import org.talosware.charis.ui.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.talosware.charis.api.GiftBasis
import org.talosware.charis.api.GiftStatus
import org.talosware.charis.api.GiftType
import org.talosware.charis.api.to.Gift
import org.talosware.charis.databinding.FragmentGiftRecyclerLayoutBinding
import org.talosware.charis.ui.components.recycler.x_recycler_view.XRecyclerView
import org.talosware.charis.ui.base.ShimmerRecyclerFragment
import org.talosware.charis.ui.gifts.GiftsFragment
import org.talosware.charis.ui.gifts.GiftsFragment.ScreenSlidePagerAdapter.Companion.REQUESTS
import org.talosware.charis.ui.gifts.GiftsViewModel
import org.talosware.charis.ui.gifts.adapter.RecyclerAdapterGifts
import org.talosware.charis.utils.DateUtils
import org.talosware.charis.utils.DateUtils.toCalendar
import org.talosware.charis.utils.GeneralDataManager
import java.util.*

@AndroidEntryPoint
class OngoingGiftsFragment: ShimmerRecyclerFragment<GiftsViewModel, Gift, FragmentGiftRecyclerLayoutBinding>(
    GiftsViewModel::class.java,
    null
) {

    override var navIconType: BaseActivity.NavIconType?
            = BaseActivity.NavIconType.BACK

    override var bottomBarType: BaseActivity.BottomBarType?
            = BaseActivity.BottomBarType.NONE

    override val headerType: BaseActivity.HeaderType?
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
                    val from = gift.calculatedDates.firstOrNull()?.time ?: 0
                    value.timeInMillis in from.minus(1)..(gift.calculatedDates.lastOrNull()
                        ?.time?.plus(1) ?: from.plus(1))
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
            if(value != null) {
                filters[GiftsFragment.GiftFilter.FROM_FRIENDS] = value to { gift ->
                    GeneralDataManager.userConnections.value?.any {
                        it.getUserInformation()?.uid == gift.senderUid
                    } == true
                }
            }else {
                filters.remove(GiftsFragment.GiftFilter.FROM_FRIENDS)
            }
            applyFilters()
        }
    }

    /** applies all filters */
    private fun applyFilters() {
        if(originalData.isEmpty()) return
        lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                val list = mutableListOf<Gift>()
                list.addAll(originalData)
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

    override fun onRefresh() {
        recyclerView.refreshComplete()
    }

    override fun onResume() {
        super.onResume()
        (parentFragment as? GiftsFragment)?.run {
            filterResponseListener = this@OngoingGiftsFragment.filterResponseListener

            changeFilters(
                filters[GiftsFragment.GiftFilter.TEXT]?.first as? String,
                filters[GiftsFragment.GiftFilter.TYPE]?.first as? GiftType,
                filters[GiftsFragment.GiftFilter.BASIS]?.first as? GiftBasis,
                filters[GiftsFragment.GiftFilter.DATE]?.first as? Calendar,
                filters[GiftsFragment.GiftFilter.FROM_FRIENDS]?.first as? Boolean
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onDataRequest(false)

        observe(GeneralDataManager.gifts) {
            lifecycleScope.launch {
                val res = sortGifts(it)
                withContext(Dispatchers.Main) {
                    hideException()
                    onDataReceived(res)
                }
            }
        }
    }

    /** sorts all gifts based on its most recent content */
    private suspend fun sortGifts(value: List<Gift>): List<Gift> {
        return withContext(Dispatchers.Default) {
            val newValue = value.filter {
                it.getUserResponse(GeneralDataManager.user?.uid ?: "")?.status == GiftStatus.ACCEPTED
            }

            var dates = newValue.flatMap { gift ->
                gift.getDatesFromPatterns(
                    anchorDate = gift.getUserResponse(GeneralDataManager.user?.uid ?: "")?.date?.toCalendar()
                ).map { gift.uid to it }
            }.toMutableList()
            dates.add("anchorIndex" to DateUtils.now().time)
            Log.d("ongoing_gifts", "dates before sorting: $dates")
            dates = dates.sortedBy { it.second?.time }.toMutableList()
            val anchorIndex = dates.indexOfFirst { it.first == "anchorIndex" }
            Log.d("ongoing_gifts", "dates after sorting: $dates, anchorIndex: $anchorIndex")
            dates = dates.subList(0, anchorIndex)
            Log.d("ongoing_gifts", "result dates: $dates")

            newValue.forEach {
                it.calculatedDates = it.getDatesFromPatterns(
                    anchorDate = it.getUserResponse(GeneralDataManager.user?.uid ?: "")?.date?.toCalendar()
                ).filterNotNull()
            }

            return@withContext newValue.sortedWith(compareByDescending<Gift> { gift ->
                dates.indexOfLast { it.first == gift.uid } }
                .thenByDescending {
                    it.creationTime
                }
            )
        }
    }

    override fun getRecyclerAdapter(data: List<Gift>): XRecyclerView.WrapAdapter<*, *> {
        return RecyclerAdapterGifts(data.toMutableList(), recyclerView).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    override fun onAdapterRefresh(data: List<Gift>) {
        (recyclerView.adapter as? RecyclerAdapterGifts)?.refreshItems(data)
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
                getString(R.string.gift_ongoing_exception_empty_title),
                getString(R.string.gift_ongoing_exception_empty_description),
                getString(R.string.gift_ongoing_exception_empty_action_primary),
                {
                    //TODO events
                    showSnackbar(baseContext.getString(R.string.snackbar_todo))
                },
                getString(R.string.gift_ongoing_exception_empty_action_secondary),
                {
                    (requireParentFragment() as? GiftsFragment)?.changeCurrentItem(REQUESTS)
                },
                viewGroup = safeBinding?.container
            )
        }
    }
}