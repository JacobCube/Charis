package org.talosware.charis.ui.gifts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.example.taloswarelib.convertDpToPx
import com.example.taloswarelib.update
import com.google.android.material.chip.Chip
import org.talosware.charis.ui.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.talosware.charis.R
import org.talosware.charis.api.GiftBasis
import org.talosware.charis.api.GiftType
import org.talosware.charis.api.to.Gift
import org.talosware.charis.databinding.FragmentMyGiftsLayoutBinding
import org.talosware.charis.ui.base.ShimmerRecyclerFragment
import org.talosware.charis.ui.components.recycler.ItemDecoration
import org.talosware.charis.ui.components.recycler.SpacingItemDecoration
import org.talosware.charis.ui.components.recycler.x_recycler_view.XRecyclerView
import org.talosware.charis.ui.gifts.adapter.RecyclerAdapterMyGifts
import org.talosware.charis.utils.GeneralDataManager
import org.talosware.charis.utils.NavigationUtils

@AndroidEntryPoint
class MyGiftsFragment: ShimmerRecyclerFragment<GiftsViewModel, Gift, FragmentMyGiftsLayoutBinding>(
    GiftsViewModel::class.java,
    null
) {

    override var navIconType: BaseActivity.NavIconType?
            = BaseActivity.NavIconType.BACK

    override var bottomBarType: BaseActivity.BottomBarType?
            = BaseActivity.BottomBarType.NONE

    override val headerType: BaseActivity.HeaderType
            = BaseActivity.HeaderType.SIMPLE

    override val loadMore: Boolean = false

    override val refreshData: Boolean = false

    //arguments
    private val args: MyGiftsFragmentArgs by navArgs()
    private var extraReceiverIds = mutableListOf<String>()
    private var extraType: GiftType? = null
    private var extraBasis: GiftBasis? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentMyGiftsLayoutBinding {
        return FragmentMyGiftsLayoutBinding.inflate(inflater, container, false)
    }

    override var recyclerViewId: Int = R.id.recycler_my_gifts

    override var shimmerItemLayout: Int = R.layout.item_gift_shimmer_layout

    override fun getRecyclerAdapter(data: List<Gift>): XRecyclerView.WrapAdapter<*, *> {
        return RecyclerAdapterMyGifts(
            data.toMutableList(),
            object: RecyclerAdapterMyGifts.MyGiftsResponse {
                override fun onItemRemoval(uid: String) {
                    GeneralDataManager.gifts.update {
                        removeIf { it.uid == uid }
                    }
                    viewModel.requestGiftRemoval(uid)
                }
            },
            recyclerView
        ).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    override fun onAdapterRefresh(data: List<Gift>) {
        (recyclerView.adapter as? RecyclerAdapterMyGifts)?.refreshItems(data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        extraReceiverIds.addAll(args.extraArgument.receiverUids)
        extraType = args.extraArgument.defaultType
        extraBasis = args.extraArgument.defaultBasis
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onDataRequest(false)

        observe(GeneralDataManager.gifts) { res ->
            lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    res.filter {
                        it.senderUid == GeneralDataManager.user?.uid
                                && it.creationTime == null
                    }.sortedByDescending { it.localCreationTime }.toMutableList().let {
                        withContext(Dispatchers.Main) {
                            hideException()
                            onDataReceived(it)
                        }
                    }
                }
            }
        }

        with(binding) {
            recyclerView.run {
                if(itemDecorationCount == 0) {
                    addItemDecoration(
                        SpacingItemDecoration(
                            footerSpace = baseContext.convertDpToPx(64f).toInt(),
                            verticalSpace = baseContext.convertDpToPx(4f).toInt()
                        )
                    )

                    ResourcesCompat.getDrawable(resources, R.drawable.horizontal_divider, baseContext.theme)?.let {
                        recyclerView.addItemDecoration(ItemDecoration(it))
                    }
                }
            }
            extraBasis?.let {
                chipGroup.addView(
                    getExtraChip(it.getEnumString(baseContext)) { chip ->
                        chipGroup.removeView(chip)
                        extraBasis = null
                    }
                )
            }
            extraType?.let {
                chipGroup.addView(
                    getExtraChip(it.getTitle(baseContext)) { chip ->
                        chipGroup.removeView(chip)
                        extraType = null
                    }
                )
            }
            extraReceiverIds.forEach { uid ->
                GeneralDataManager.userConnections.value?.find { it.getUserInformation()?.uid == uid }
                    ?.getUserInformation()?.displayName?.let {
                        chipGroup.addView(
                            getExtraChip(it) { chip ->
                                chipGroup.removeView(chip)
                                extraReceiverIds.remove(uid)
                            }
                        )
                    }
            }

            imgNewGift.setOnClickListener {
                NavigationUtils.navigateToGiftBuilder(
                    baseActivity,
                    extraReceiverIds,
                    defaultBasis = extraBasis,
                    defaultType = extraType
                )
                clearArguments()
            }
        }
    }

    override fun onDataRequest(isSpecial: Boolean) {
        super.onDataRequest(isSpecial)
        viewModel.requestMyGifts()
    }

    override fun onItemClick(view: View?, position: Int) {
        super.onItemClick(view, position)
        data.getOrNull(position)?.let {
            NavigationUtils.navigateToGiftBuilder(
                baseActivity,
                localGiftId = it.uid,
                receiverIds = extraReceiverIds,
                defaultType = extraType,
                defaultBasis = extraBasis
            )
            clearArguments()
        }
    }

    override fun showEmptyView() {
        showException(
            R.drawable.ill_empty,
            getString(R.string.my_gifts_exception_empty_title),
            getString(R.string.my_gifts_exception_empty_description),
            getString(R.string.my_gifts_exception_empty_action_primary),
            {
                NavigationUtils.navigateToGiftBuilder(
                    baseActivity,
                    extraReceiverIds,
                    null,
                    extraType,
                    extraBasis,
                    true
                )
                clearArguments()
            },
            getString(R.string.my_gifts_exception_empty_action_secondary),
            {
                NavigationUtils.navigateToGiftBuilder(
                    baseActivity,
                    extraReceiverIds,
                    null,
                    extraType,
                    extraBasis
                )
                clearArguments()
            },
            viewGroup = safeBinding?.root
        )
    }

    /** clears local extras/arguments */
    private fun clearArguments() {
        extraReceiverIds.clear()
        extraType = null
        extraBasis = null
    }

    /** returns new entry chip for extra data at the bottom of the screen */
    private fun getExtraChip(text: String, onClick: (c: Chip) -> Unit): Chip? {
        return (View.inflate(baseContext, R.layout.entry_chip_layout, null) as? Chip)?.apply {
            id = View.generateViewId()
            this.text = text
            setOnCloseIconClickListener {
                onClick.invoke(this)
            }
            setOnCheckedChangeListener { _, isChecked ->
                if(isChecked.not()) {
                    onClick.invoke(this)
                }
            }
        }
    }
}