package org.talosware.charis.api.to

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.afollestad.date.year
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import org.talosware.charis.api.GiftFocusType
import org.talosware.charis.api.GiftBasis
import org.talosware.charis.api.GiftType
import org.talosware.charis.utils.DateUtils
import org.talosware.charis.utils.DateUtils.endOfDay
import org.talosware.charis.utils.DateUtils.formatToString
import org.talosware.charis.utils.DateUtils.getCalendarField
import org.talosware.charis.utils.DateUtils.toCalendar
import org.talosware.charis.utils.GeneralDataManager
import org.talosware.charis.utils.Tools.capitalize
import java.io.Serializable
import java.time.YearMonth
import java.util.*
import kotlin.collections.HashMap

/**
 * User Gift from a user to user
 */
@Entity(tableName = "gift_table")
data class Gift(

    /** display names of all involved parties */
    var involvedPartiesUid: MutableList<String> = mutableListOf(),

    /** unique identification */
    @PrimaryKey
    var uid: String = UUID.randomUUID().toString(),

    /** User ids of receivers of this Gift */
    var receiverIds: MutableList<String> = mutableListOf(),

    /** Description from sender */
    var description: String = "",

    /** Title from sender */
    var title: String = "",

    /** Name of user from whom gift was given */
    var senderDisplayName: String = GeneralDataManager.username,

    /** User id of creator/sender of this Gift */
    var senderUid: String = GeneralDataManager.user?.uid ?: "",

    /** Type of gift
     * @see GiftType */
    var type: GiftType? = null,

    /**
     * Type of audience and what's the use of this gift
     */
    var focusType: GiftFocusType = GiftFocusType.PERSONAL,

    /** Content - notifications that will be sent
     * @see GiftNotification
     */
    var content: MutableList<GiftNotification> = mutableListOf(),

    /** Time of creation */
    @ServerTimestamp
    var creationTime: Date? = null,

    /** Local time of creation */
    var localCreationTime: Date = DateUtils.now().time,

    /** Date of activation, pattern: dd-MM-yyyy */
    var activationDate: GiftPatternDate? = null,

    /** current responses from receiving users */
    var response: HashMap<String, GiftResponse> = hashMapOf(),

    /** selected date patterns */
    var selectedDatePatterns: MutableList<GiftPatternDate> = mutableListOf(),

    /**
     * manually selected dates via [GiftBasis.CUSTOM], with timezone.
     * Main use: global events, probably marketing purposes etc.
     */
    var customSelectedDatesTimeZone: MutableList<Date> = mutableListOf(),

    /** whether selected dates are global and we don't really care about date in other Timezones */
    var isCustomTimeZoneMode: Boolean = false,

    /** whether selected dates in a month are focused on days of week */
    var isMonthWeekDayMode: Boolean = false,

    /** on what basis is gift designed to work */
    var basis: GiftBasis? = null,

    /**
     * expected schedule hour of notifications, receiver can modify this
     */
    var notificationHour: Int = 12,

    /**
     * expected schedule minutes of notifications, receiver can modify this
     */
    var notificationMinutes: Int = 0,

    /**
     * Custom icon Id in case user has chosen it
     */
    var iconId: String = "",

) : Serializable {

    /**
     * image url of custom notification icon/picture
     */
    val iconUrl: String?
        @Exclude
        get() = GeneralDataManager.userNotificationIcons?.get(iconId)

    @Ignore
    /** currently LOCALLY estimated dates */
    var calculatedDates = listOf<Date>()
    //@Exclude get

    /** returns calculated dates mapped to its content */
    fun getDatesToContent(anchorDate: Calendar?) {
        if(calculatedDates.isEmpty()) {
            calculatedDates = getDatesFromPatterns().filterNotNull()
        }
        val dates = mutableListOf<Date>()
        dates.addAll(calculatedDates)
    }

    @Ignore
    @Exclude
    /** returns lifetime range string for user */
    fun getLifetimeRange(): String {
        return if(calculatedDates.isEmpty()) {
            ""
        }else {
            //DIFFERENT YEAR, SHOW GENERIC PATTERN
            if(calculatedDates.first().getCalendarField(Calendar.YEAR) != DateUtils.now().year) {
                if(calculatedDates.size == 1) {
                    calculatedDates.first().formatToString("d.M.yyyy")
                }else {
                    "${calculatedDates.first().formatToString("d.M.yyyy")} - ${calculatedDates.last().formatToString("d.M.yyyy")}"
                }
            }else {
                if(calculatedDates.size == 1) {
                    calculatedDates.first().formatToString("LLLL d.").capitalize()
                }else {
                    calculatedDates.first().formatToString("LLLL d.").capitalize() +
                            " - ${calculatedDates.last().formatToString("LLLL d.").capitalize()}"
                }
            }
        }
    }

    /**
     * whether is gift accepted by current user or not
     * @param uid uid of a user we wawnt to know status of
     */
    @Ignore
    @Exclude
    fun getUserResponse(uid: String): GiftResponse? {
        return response.getOrDefault(uid, null)
    }

    /** whether this gift is archived by current user */
    @Ignore
    fun isArchived(): Boolean {
        return GeneralDataManager.user?.archivedGifts?.contains(uid) == true
    }

    /** checks/invalidates pattern standards and aligns it with expected values if needed */
    fun invalidatePattern() {
        if(selectedDatePatterns.size > getPatternSize()) {
            val res = selectedDatePatterns.sortedBy { it.sortableString(this) }.toMutableList()
            selectedDatePatterns = res.subList(0, getPatternSize().minus(1))
        }
    }

    /**
     * Returns dates assigned to selected patterns
     * @param limit how many dates do we want based on current pattern
     */
    @Ignore
    fun getDatesFromPatterns(limit: Int = content.size, anchorDate: Calendar? = null): List<Date?> {
        val dates = mutableListOf<Date?>()
        var lastIndex = 0
        for(i in 0 until limit) {
            getNotificationDate(
                lastIndex,
                anchorDate = anchorDate
            ).let {
                dates.add(it.first)
                lastIndex = it.second.plus(1)
            }
        }
        return dates
    }

    /**
     * index of notification, in order to calculate
     * date when the notification should be executed
     * @return result pair of: date and index of round/counter
     */
    @Ignore
    @Exclude
    fun getNotificationDate(
        index: Int,
        executionHour: Int? = null,
        executionMinute: Int? = null,
        anchorDate: Calendar? = null
    ): Pair<Date?, Int> {
        invalidatePattern()

        val res = when(basis) {
            GiftBasis.DAILY -> {
                (activationDate?.toCalendar() ?: DateUtils.now()).apply {
                    add(Calendar.DAY_OF_YEAR, index)
                    set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                    set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                    set(Calendar.SECOND, 0)
                }.time
            }
            GiftBasis.WEEKLY -> {
                var weeks = 0
                var realIndex = index
                if(index >= selectedDatePatterns.size) {
                    weeks = kotlin.math.floor(
                        index.toDouble().div(selectedDatePatterns.size.toDouble())
                    ).toInt()
                    realIndex = index.minus(weeks.times(selectedDatePatterns.size))
                }
                selectedDatePatterns.sortedBy { it.sortableString(this) }.getOrNull(realIndex)?.run {
                    (activationDate?.toCalendar() ?: DateUtils.now()).apply {
                        add(Calendar.WEEK_OF_YEAR, weeks)
                        set(Calendar.DAY_OF_WEEK, this@run.day)
                        set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                        set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                        set(Calendar.SECOND, 0)
                    }.time
                }
            }
            GiftBasis.MONTHLY -> {
                if(isMonthWeekDayMode) {
                    var months = 0
                    var realIndex = index
                    if(index >= selectedDatePatterns.size) {
                        months = kotlin.math.floor(
                            index.toDouble().div(selectedDatePatterns.size.toDouble())
                        ).toInt()
                        realIndex = index.minus(months.times(selectedDatePatterns.size))
                    }
                    selectedDatePatterns.sortedBy { it.sortableString(this) }.getOrNull(realIndex)?.run {
                        (activationDate?.toCalendar() ?: DateUtils.now()).apply {
                            add(Calendar.MONTH, months)
                            set(Calendar.DAY_OF_MONTH, 1)
                            set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                            set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                            set(Calendar.SECOND, 0)
                        }.also {
                            val changedDate = (activationDate?.toCalendar() ?: DateUtils.now()).apply {
                                add(Calendar.MONTH, months)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.DAY_OF_WEEK, day)
                                set(Calendar.DAY_OF_WEEK_IN_MONTH, week)
                                set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                                set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                                set(Calendar.SECOND, 0)
                            }

                            if(changedDate.get(Calendar.MONTH) != it.get(Calendar.MONTH)) {
                                return@getNotificationDate getNotificationDate(
                                    index.plus(1),
                                    executionHour,
                                    executionMinute,
                                    anchorDate
                                )
                            }else {
                                it.set(Calendar.DAY_OF_WEEK, day)
                                it.set(Calendar.DAY_OF_WEEK_IN_MONTH, week)
                            }
                        }.time
                    }
                }else {
                    var months = 0
                    var realIndex = index
                    if(index >= selectedDatePatterns.size) {
                        months = kotlin.math.floor(
                            index.toDouble().div(selectedDatePatterns.size.toDouble())
                        ).toInt()
                        realIndex = index.minus(months.times(selectedDatePatterns.size))
                    }
                    selectedDatePatterns.sortedBy { it.sortableString(this) }.getOrNull(realIndex)?.run {
                        (activationDate?.toCalendar() ?: DateUtils.now()).apply {
                            add(Calendar.MONTH, months)
                            set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                            set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                            set(Calendar.SECOND, 0)
                        }.also {
                            val monthDays = YearMonth.of(
                                it.get(Calendar.YEAR),
                                it.get(Calendar.MONTH).plus(1)
                            ).lengthOfMonth()

                            if(this.day > monthDays) {
                                return@getNotificationDate getNotificationDate(
                                    index.plus(1),
                                    executionHour,
                                    executionMinute,
                                    anchorDate
                                )
                            }else {
                                it.set(Calendar.DAY_OF_MONTH, this@run.day)
                            }
                        }.time
                    }
                }
            }
            GiftBasis.YEARLY -> {
                var years = 0
                var realIndex = index
                if(index >= selectedDatePatterns.size) {
                    years = kotlin.math.floor(
                        index.toDouble().div(selectedDatePatterns.size.toDouble())
                    ).toInt()
                    realIndex = index.minus(years.times(selectedDatePatterns.size))
                }
                selectedDatePatterns.sortedBy { it.sortableString(this) }.getOrNull(realIndex)?.run {
                    (DateUtils.now()).apply {
                        set(Calendar.MONTH, this@run.month - 1)
                        add(Calendar.YEAR, years)
                        set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                        set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                        set(Calendar.SECOND, 0)
                    }.also {
                        val monthDays = YearMonth.of(
                            it.get(Calendar.YEAR),
                            this@run.month
                        ).lengthOfMonth()

                        if(this.day > monthDays) {
                            return@getNotificationDate getNotificationDate(
                                index.plus(1),
                                executionHour,
                                executionMinute,
                                anchorDate
                            )
                        }else {
                            it.set(Calendar.DAY_OF_MONTH, this@run.day)
                        }
                    }.time
                }
            }
            GiftBasis.CUSTOM -> {
                if(isCustomTimeZoneMode) {
                    customSelectedDatesTimeZone.sortedByDescending { it.time }.getOrNull(index)
                        ?.toCalendar()
                        ?.apply {
                            set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                            set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                            set(Calendar.SECOND, 0)
                        }?.time
                }else {
                    selectedDatePatterns.sortedBy { it.sortableString(this) }.getOrNull(index)?.toCalendar()?.apply {
                        set(Calendar.HOUR_OF_DAY, executionHour ?: notificationHour)
                        set(Calendar.MINUTE, executionMinute ?: notificationMinutes)
                        set(Calendar.SECOND, 0)
                    }?.time
                }
            }
            else -> null
        }

        //activation date check
        return if((activationDate != null && res?.before(activationDate?.toCalendar()?.time) == true)
            || res?.before((anchorDate ?: DateUtils.now()).endOfDay()?.time) == true) {
            getNotificationDate(
                index.plus(1),
                executionHour,
                executionMinute,
                anchorDate
            )
        }else {
            res to index
        }
    }

    /**
     * day pattern limit for current basis
     */
    @Ignore
    @Exclude
    fun getPatternSize(): Int {
        return when(basis) {
            GiftBasis.DAILY -> 1
            GiftBasis.WEEKLY -> 7
            GiftBasis.MONTHLY -> 31
            GiftBasis.YEARLY -> 366
            GiftBasis.CUSTOM -> Int.MAX_VALUE
            else -> 0
        }
    }
}