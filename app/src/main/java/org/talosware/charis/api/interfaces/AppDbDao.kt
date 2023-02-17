package org.talosware.charis.api.interfaces

import android.content.Context
import android.util.Log
import androidx.room.*
import org.talosware.charis.api.to.*
import org.talosware.charis.utils.DateUtils


@Dao
interface AppDbDao {

    //------------------- GIFT ---------------------

    @Query("SELECT * FROM gift_table ORDER BY creationTime ASC")
    suspend fun getAllGifts(): List<Gift>?

    @Query("SELECT * FROM gift_table WHERE :uid IN(involvedPartiesUid) ORDER BY creationTime ASC")
    suspend fun getUserGifts(uid: String): List<Gift>?

    @Query("SELECT * FROM gift_table WHERE uid == :uid LIMIT 1")
    suspend fun getGift(uid: String): Gift?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addGift(gift: Gift)

    @Query("DELETE FROM gift_table WHERE uid == :identification")
    suspend fun removeGift(identification: String = "")

    @Query("DELETE FROM gift_table")
    suspend fun deleteAllGifts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateGift(gift: Gift)

    //------------------- USER ---------------------

    @Query("SELECT * FROM external_user_table ORDER BY creationTime ASC")
    suspend fun getAllUsers(): List<ExternalUser>?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addUser(user: ExternalUser)

    @Query("SELECT * FROM external_user_table WHERE uid == :uid LIMIT 1")
    suspend fun getExternalUser(uid: String): ExternalUser?

    @Query("DELETE FROM external_user_table WHERE uid == :identification")
    suspend fun removeUser(identification: String = "")

    @Query("DELETE FROM external_user_table")
    suspend fun deleteAllUsers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateUser(user: ExternalUser)

    //------------------- CONFIG ---------------------

    @Query("SELECT * FROM config_public_table WHERE configVersionCode == :versionCode ORDER BY configVersionCode DESC LIMIT 1")
    suspend fun getPublicConfig(versionCode: Int): ConfigPublic?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updatePublicConfig(configPublic: ConfigPublic)

    //------------------- GIFT NOTIFICATION ---------------------

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGiftNotification(giftNotification: GiftNotification)

    @Query("SELECT * FROM gift_notification_table WHERE uid == :uid LIMIT 1")
    suspend fun getGiftNotification(uid: String): GiftNotification?

    @Query("SELECT * FROM gift_notification_table WHERE isFinished == :isFinished")
    suspend fun getPlannedGiftNotifications(isFinished: Boolean = false): List<GiftNotification>?

    @Query("UPDATE gift_notification_table SET isFinished = :isFinished WHERE uid == :uid")
    suspend fun updateGiftNotificationStatus(uid: String, isFinished: Boolean)

    /** plans gift notifications based on current data and incoming gift */
    suspend fun planGiftNotifications(
        context: Context,
        gift: Gift,
        executionHour: Int? = null,
        executionMinute: Int? = null
    ) {
        val dates = gift.getDatesFromPatterns(gift.apply {
            notificationHour = executionHour ?: notificationHour
            notificationMinutes = executionMinute ?: notificationMinutes
        }.content.size)

        dates.filterNotNull().forEachIndexed { index, date ->
                gift.content.getOrNull(index)?.let { giftNotification ->
                val localGiftNotification = getGiftNotification(giftNotification.uid)

                //only upcoming and non-existant notifications
                    if ((localGiftNotification == null || localGiftNotification.isFinished.not())
                        && date.after(DateUtils.now().time)) {

                        insertGiftNotification(giftNotification.apply {
                            giftType = gift.type
                            expectedExecutionDate = date
                        })
                    }
            }
        }
    }
}