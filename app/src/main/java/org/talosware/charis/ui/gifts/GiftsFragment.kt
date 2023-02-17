package org.talosware.charis.ui.gifts

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.afollestad.materialdialogs.DialogCallback
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.datetime.DateTimeCallback
import com.example.taloswarelib.reduceDragSensitivity
import com.example.taloswarelib.showOrGone
import com.google.android.material.tabs.TabLayoutMediator
import org.talosware.charis.R
import org.talosware.charis.ui.base.BaseActivity
import org.talosware.charis.ui.base.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import org.talosware.charis.api.GiftBasis
import org.talosware.charis.api.GiftType
import org.talosware.charis.databinding.FragmentGiftsLayoutBinding
import org.talosware.charis.ui.gifts.GiftsFragment.ScreenSlidePagerAdapter.Companion.REQUESTS
import org.talosware.charis.ui.gifts.fragments.ArchivedGiftsFragment
import org.talosware.charis.ui.gifts.fragments.GiftRequestsFragment
import org.talosware.charis.ui.gifts.fragments.OngoingGiftsFragment
import org.talosware.charis.utils.DateUtils
import org.talosware.charis.utils.DateUtils.formatToString
import org.talosware.charis.utils.ViewUtils
import java.util.*

@AndroidEntryPoint
class GiftsFragment : BaseFragment<GiftsViewModel, FragmentGiftsLayoutBinding>(
    GiftsViewModel::class.java
) {

    /** gifts category type */
    enum class GiftListType {
        REQUESTS,
        ONGOING,
        ARCHIVED;

        /** @return returns technical name of category */
        fun getListName(context: Context): String {
            return context.getString(
                when (this) {
                    REQUESTS -> R.string.gifts_switch_0
                    ONGOING -> R.string.gifts_switch_1
                    ARCHIVED -> R.string.gifts_switch_2
                }
            )
        }
    }

    /** types of gift filters */
    enum class GiftFilter {
        DATE,
        TYPE,
        TEXT,
        BASIS,
        FROM_FRIENDS
    }

    /** communication channel between global filters and individual fragments */
    interface GiftsFilterResponse {
        fun onTextFilterChanged(value: String?)
        fun onDateFilterSelected(value: Calendar?)
        fun onTypeFilterSelected(value: GiftType?)
        fun onBasisFilterSelected(value: GiftBasis?)
        fun onOnlyFriendsChanged(value: Boolean?)
    }

    /** current filter listener */
    var filterResponseListener: GiftsFilterResponse? = null

    //arguments
    private val args: GiftsFragmentArgs by navArgs()

    override var navIconType: BaseActivity.NavIconType?
            = BaseActivity.NavIconType.BACK

    override var bottomBarType: BaseActivity.BottomBarType?
            = BaseActivity.BottomBarType.NONE

    override val headerType: BaseActivity.HeaderType
            = BaseActivity.HeaderType.GIFTS

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        safeBinding?.run {
            //pager2_gifts.isUserInputEnabled = false
            //pager2_gifts.setPageTransformer(ListPageTransformer())

            pager2Gifts.run {
                adapter = ScreenSlidePagerAdapter(childFragmentManager, lifecycle)
                reduceDragSensitivity()
                offscreenPageLimit = 3
                setCurrentItem(args.viewPagerIndex, false)
            }

            TabLayoutMediator(switchGroupGifts, pager2Gifts) { tab, position ->
                tab.text = GiftListType.values()[position].getListName(baseContext)
            }.attach()

            switchGroupGifts.addOnItemSelectedListener { _, position ->
                chipFriendOnly.showOrGone(position != REQUESTS)
            }

            editTextMyGiftsSearch.doOnTextChanged { text, _, _, _ ->
                filterResponseListener?.onTextFilterChanged(if(text.isNullOrEmpty()) {
                    null
                }else text.toString())
            }

            chipDate.run {
                val filterDialog = { isEdit: Boolean ->
                    var isAnswered = false
                    ViewUtils.makeDateDialog(
                        baseContext,
                        title = getString(R.string.gift_filter_date_dialog_title),
                        minDate = DateUtils.now().apply {
                            set(Calendar.YEAR, 2021)
                            set(Calendar.DAY_OF_YEAR, 1)
                        },
                        maxDate = DateUtils.now().apply {
                            add(Calendar.YEAR, 100)
                        },
                        currentDate = currentDateFilter ?: DateUtils.now(),
                        dateCallback = object : DateTimeCallback {
                            override fun invoke(dialog: MaterialDialog, datetime: Calendar) {
                                filterResponseListener?.onDateFilterSelected(datetime)
                                isAnswered = true
                                changeFilters(
                                    dateFilter = datetime,
                                    isEdit = true
                                )
                            }
                        },
                        positiveButtonText = getString(R.string.action_ok),
                        negativeButtonText = getString(R.string.action_cancel),
                        negativeButtonCallback = object : DialogCallback {
                            override fun invoke(p1: MaterialDialog) {

                            }
                        },
                        onDismiss = {
                            chipDate.isChecked = isAnswered || (isEdit && currentDateFilter != null)
                        }
                    )
                }

                setOnLongClickListener {
                    filterDialog.invoke(true)
                    true
                }

                setOnCheckedChangeListener { _, isChecked ->
                    if(isPressed.not()) return@setOnCheckedChangeListener
                    if(isChecked) {
                        filterDialog.invoke(false)
                    }else {
                        text = getString(R.string.gift_filter_date)
                        filterResponseListener?.onDateFilterSelected(null)
                        currentDateFilter = null
                    }
                }
            }

            chipBasis.run {
                val filterDialog = { isEdit: Boolean ->
                    var isAnswered = false
                    ViewUtils.makeGiftBasisDialog(
                        baseContext,
                        getString(R.string.gift_filter_basis_dialog_title),
                        "",
                        getString(R.string.action_confirm),
                        {
                            filterResponseListener?.onBasisFilterSelected(it)
                            isAnswered = true
                            changeFilters(
                                basisFilter = it,
                                isEdit = true
                            )
                        },
                        getString(R.string.action_cancel),
                        onDismiss = {
                            chipBasis.isChecked = isAnswered || (isEdit && currentBasisFilter != null)
                        },
                        defaultValue = currentBasisFilter
                    )
                }

                setOnLongClickListener {
                    filterDialog.invoke(true)
                    true
                }

                setOnCheckedChangeListener { _, isChecked ->
                    if(isPressed.not()) return@setOnCheckedChangeListener
                    if(isChecked) {
                        filterDialog.invoke(false)
                    }else {
                        text = getString(R.string.gift_filter_basis)
                        filterResponseListener?.onBasisFilterSelected(null)
                        currentBasisFilter = null
                    }
                }
            }

            chipType.run {
                val filterDialog = { isEdit: Boolean ->
                    var isAnswered = false
                    ViewUtils.makeGiftTypeDialog(
                        baseContext,
                        getString(R.string.gift_filter_type_dialog_title),
                        "",
                        getString(R.string.action_confirm),
                        {
                            filterResponseListener?.onTypeFilterSelected(it)
                            isAnswered = true
                            changeFilters(
                                typeFilter = it,
                                isEdit = true
                            )
                        },
                        getString(R.string.action_cancel),
                        onDismiss = {
                            chipType.isChecked = isAnswered || (isEdit && currentTypeFilter != null)
                        },
                        defaultValue = currentTypeFilter
                    )
                }

                setOnLongClickListener {
                    filterDialog.invoke(true)
                    true
                }

                setOnCheckedChangeListener { _, isChecked ->
                    if(isPressed.not()) return@setOnCheckedChangeListener
                    if(isChecked) {
                        filterDialog.invoke(false)
                    }else {
                        text = getString(R.string.gift_filter_type)
                        filterResponseListener?.onTypeFilterSelected(null)
                        currentTypeFilter = null
                    }
                }
            }

            chipFriendOnly.run {
                setOnCheckedChangeListener { _, isChecked ->
                    if(isPressed.not()) return@setOnCheckedChangeListener
                    if(isChecked) {
                        filterResponseListener?.onOnlyFriendsChanged(true)
                    }else {
                        text = getString(R.string.gift_filter_friend_only)
                        filterResponseListener?.onOnlyFriendsChanged(null)
                    }
                }
            }
        }
    }

    /** current temporary values tied to current page fragment */
    private var currentTypeFilter: GiftType? = null
    private var currentBasisFilter: GiftBasis? = null
    private var currentDateFilter: Calendar? = null

    /** changes UI filters programmatically */
    fun changeFilters(
        textFilter: String? = null,
        typeFilter: GiftType? = null,
        basisFilter: GiftBasis? = null,
        dateFilter: Calendar? = null,
        onlyFriendsFilter: Boolean? = null,
        isEdit: Boolean = false
    ) {

        if(isEdit) {
            typeFilter?.let {
                currentTypeFilter = it
            }
            basisFilter?.let {
                currentBasisFilter = it
            }
            dateFilter?.let {
                currentDateFilter = it
            }
        }else {
            currentTypeFilter = typeFilter
            currentBasisFilter = basisFilter
            currentDateFilter = dateFilter
        }

        safeBinding?.run {
            if(isEdit.not()) {
                editTextMyGiftsSearch.setText(textFilter)
            }

            chipType.isChecked = currentTypeFilter != null
            chipType.text = currentTypeFilter?.getTitle(baseContext) ?: getString(R.string.gift_filter_type)

            chipBasis.isChecked = currentBasisFilter != null
            chipBasis.text = currentBasisFilter?.getEnumString(baseContext) ?: getString(R.string.gift_filter_basis)

            chipDate.isChecked = currentDateFilter != null
            chipDate.text = currentDateFilter?.time?.formatToString("d.M.yyyy") ?: getString(R.string.gift_filter_date)

            if(isEdit.not()) {
                chipFriendOnly.isChecked = onlyFriendsFilter != null
            }
        }
    }

    /** changes current viewpager item */
    fun changeCurrentItem(index: Int) {
        safeBinding?.pager2Gifts?.setCurrentItem(index, true)
    }

    class ScreenSlidePagerAdapter(fm: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fm, lifecycle) {

        companion object {
            const val REQUESTS = 0
            const val ONGOING = 1
            const val ARCHIVED = 2
        }

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when(position) {
                REQUESTS -> GiftRequestsFragment()
                ONGOING -> OngoingGiftsFragment()
                else -> ArchivedGiftsFragment() //ARCHIVED
            }
        }
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentGiftsLayoutBinding {
        return FragmentGiftsLayoutBinding.inflate(inflater, container, false)
    }
}