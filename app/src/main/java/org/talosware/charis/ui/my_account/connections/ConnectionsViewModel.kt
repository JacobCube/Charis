package org.talosware.charis.ui.my_account.connections

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.taloswarelib.update
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.talosware.charis.api.LoadingListenerApi
import org.talosware.charis.api.LoadingStatusType
import org.talosware.charis.api.to.ExternalUser
import org.talosware.charis.api.to.UserConnectionStatus
import org.talosware.charis.api.to.UserConnection
import org.talosware.charis.api.to.UserConnectionType
import org.talosware.charis.ui.base.BaseRepository
import org.talosware.charis.ui.base.BaseRepository.PreferenceUtils.AGREED_DAYS_LOAD_DEFAULT
import org.talosware.charis.ui.base.BaseViewModel
import org.talosware.charis.utils.DateUtils
import org.talosware.charis.utils.GeneralDataManager
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: ConnectionsRepository,
    val dataManager: ConnectionsDataManager
) : BaseViewModel(repository) {

    /** fills all required data for the downloaded friend list */
    fun fillFriendListData(
        context: Context,
        forcedReload: Boolean
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // get data from room database
                val allUsers = mutableListOf<ExternalUser>()
                val isAgreedUpdate = BaseRepository.PreferenceUtils.getFriendsUpdateDate(context)
                    .before(DateUtils.now().apply {
                        add(
                            Calendar.DAY_OF_YEAR,
                            -(GeneralDataManager.config.value?.agreedDataRefreshDays ?: AGREED_DAYS_LOAD_DEFAULT)
                        )
                    }.time)
                // download local data only if we are certain update is not necessary
                if(isAgreedUpdate.not()) {
                    allUsers.addAll(repository.appDbDao.getAllUsers().orEmpty())
                }

                if(forcedReload || isAgreedUpdate || allUsers.isEmpty()) {
                    BaseRepository.PreferenceUtils.saveFriendsUpdateDate(context)
                    //fetch user connections
                    val missingData = mutableListOf<UserConnection>()
                    GeneralDataManager.userConnections.value?.let {
                        missingData.addAll(it)
                    }
                    withContext(Dispatchers.Default) {
                        missingData.filter {
                            it.connectionType == UserConnectionType.FRIEND
                                    && it.status == UserConnectionStatus.ACCEPTED
                        }.run {
                            mapNotNull { it.getUserInformation()?.displayName }.let { displayNames ->
                                if (displayNames.isNotEmpty()) {
                                    repository.updateFriendListByName(displayNames)?.forEach { user ->
                                        repository.appDbDao.updateUser(user.apply {
                                            lastForceUpdate = DateUtils.now().time
                                        })
                                        GeneralDataManager.userConnections.value?.find {
                                            it.getUserInformation()?.uid == user.uid
                                        }?.run {
                                            if(amISource) {
                                                targetBlueberries = user.blueBerries
                                                targetProfileId = user.profilePictureId
                                            }else {
                                                sourceBlueberries = user.blueBerries
                                                targetProfileId = user.profilePictureId
                                            }
                                        }
                                    }
                                    GeneralDataManager.userConnections.update()
                                }
                            }
                        }
                    }
                }else {
                    withContext(Dispatchers.Default) {
                        allUsers.forEach { user ->
                            GeneralDataManager.userConnections.value?.find {
                                it.getUserInformation()?.uid == user.uid
                            }?.run {
                                if(amISource) {
                                    targetBlueberries = user.blueBerries
                                    targetProfileId = user.profilePictureId
                                }else {
                                    sourceBlueberries = user.blueBerries
                                    targetProfileId = user.profilePictureId
                                }
                            }
                            GeneralDataManager.userConnections.update()
                        }
                    }
                }
            }
        }
    }

    /**
     * send connection request to given username
     * @param username display name of a target user
     */
    fun sendConnectionRequest(
        username: String,
        userConnectionType: UserConnectionType,
        comment: String
    ) {
        val userConnection = UserConnection(
            involvedPartiesDisplayName = listOf(GeneralDataManager.username, username),
            sourceDisplayName = GeneralDataManager.username,
            sourceBlueberries = GeneralDataManager.user?.blueBerries,
            sourceProfileId = GeneralDataManager.user?.profilePictureId ?: "",
            connectionType = userConnectionType,
            targetDisplayName = username,
            sourceUid = GeneralDataManager.user?.uid ?: "error",
            status = UserConnectionStatus.WAITING,                         //TODO just local, server shouldn't allow this
            comment = comment
        )
        viewModelScope.launch {
            repository.addNewUserConnection(userConnection)?.addOnCompleteListener {
                dataManager.sentConnectionRequest.postValue(
                    if(it.isSuccessful) {
                        dataManager.base.addLoading(
                            LoadingListenerApi(key = ConnectionsAddFragment.NEW_CONNECTION_UPLOAD_KEY),
                            LoadingStatusType.COMPLETED
                        )
                        userConnection
                    }else {
                        dataManager.base.addLoading(
                            LoadingListenerApi(key = ConnectionsAddFragment.NEW_CONNECTION_UPLOAD_KEY),
                            LoadingStatusType.ERROR
                        )
                        null
                    }
                )
            }
        }
    }
}