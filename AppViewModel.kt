package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = AppRepository(application, database.appDao())

    private val sharedPrefs = application.getSharedPreferences("gmail_reward_prefs", Context.MODE_PRIVATE)

    // --- State Streams ---
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _currentModerator = MutableStateFlow<ModeratorEntity?>(null)
    val currentModerator: StateFlow<ModeratorEntity?> = _currentModerator.asStateFlow()

    private val _assignedTaskForMod = MutableStateFlow<GmailSubmissionEntity?>(null)
    val assignedTaskForMod: StateFlow<GmailSubmissionEntity?> = _assignedTaskForMod.asStateFlow()

    val allModerators: StateFlow<List<ModeratorEntity>> = repository.getAllModeratorsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVerificationLogs: StateFlow<List<VerificationLogEntity>> = repository.getAllVerificationLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allModeratorPayments: StateFlow<List<ModeratorPaymentEntity>> = repository.getAllModeratorPaymentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingSubmissions: StateFlow<List<GmailSubmissionEntity>> = repository.getPendingSubmissionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val submissions: StateFlow<List<GmailSubmissionEntity>> = repository.allSubmissions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val withdrawals: StateFlow<List<WithdrawalEntity>> = repository.allWithdrawals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<List<SettingEntity>> = repository.allSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bannedDomains: StateFlow<List<BannedDomainEntity>> = repository.allBannedDomains
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Dynamic User Submissions & Withdrawals Filtered ---
    val userSubmissions: StateFlow<List<GmailSubmissionEntity>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                database.appDao().getSubmissionsByUserFlow(user.email)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userWithdrawals: StateFlow<List<WithdrawalEntity>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                database.appDao().getWithdrawalsByUserFlow(user.email)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI State Management ---
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _internetConnected = MutableStateFlow(true)
    val internetConnected: StateFlow<Boolean> = _internetConnected.asStateFlow()

    init {
        // Restore session
        val savedEmail = sharedPrefs.getString("logged_in_user_email", null)
        if (savedEmail != null) {
            viewModelScope.launch {
                val user = repository.getUser(savedEmail)
                if (user != null && !user.isBanned) {
                    _currentUser.value = user
                    // Keep user in sync with real-time updates
                    observeCurrentUser(savedEmail)
                } else {
                    sharedPrefs.edit().remove("logged_in_user_email").apply()
                }
            }
        }
    }

    private fun observeCurrentUser(email: String) {
        viewModelScope.launch {
            database.appDao().getAllUsersFlow().collect { list ->
                val updated = list.find { it.email == email }
                if (updated != null) {
                    if (updated.isBanned) {
                        logout()
                        _uiState.value = UiState.Error("Your account was suspended by Admin.")
                    } else {
                        _currentUser.value = updated
                    }
                }
            }
        }
    }

    // --- Authentication Actions ---
    fun loginWithGoogleGmail(email: String, name: String, referralCode: String? = null) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            if (!email.endsWith("@gmail.com")) {
                _uiState.value = UiState.Error("Authentication error: Please login using a valid Google @gmail.com account.")
                return@launch
            }

            try {
                // Securely query or create user record
                val user = repository.getOrCreateUser(email, name, referralCode)
                if (user.isBanned) {
                    _uiState.value = UiState.Error("Your account was suspended due to duplicate device policies.")
                    return@launch
                }
                
                sharedPrefs.edit().putString("logged_in_user_email", email).apply()
                _currentUser.value = user
                _uiState.value = UiState.Success("Welcome, ${user.displayName}!")
                observeCurrentUser(email)
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Google Authentication Failed: ${e.localizedMessage}")
            }
        }
    }

    fun logout() {
        sharedPrefs.edit().remove("logged_in_user_email").apply()
        _currentUser.value = null
        _uiState.value = UiState.Idle
    }

    // --- Submission Tasks ---
    fun submitGmailTask(submittedGmail: String, passwordPlain: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val (success, message) = repository.submitGmailTask(user.email, submittedGmail, passwordPlain)
            if (success) {
                _uiState.value = UiState.Success(message)
            } else {
                _uiState.value = UiState.Error(message)
            }
        }
    }

    // --- Withdrawal Requester ---
    fun submitWithdrawal(amount: Int, upiId: String, name: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val (success, message) = repository.requestWithdrawal(user.email, amount, upiId, name)
            if (success) {
                _uiState.value = UiState.Success(message)
            } else {
                _uiState.value = UiState.Error(message)
            }
        }
    }

    // --- Daily Bonus ---
    fun claimDailyBonus() {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val bonusGained = repository.claimDailyBonus(user.email)
            if (bonusGained != null) {
                _uiState.value = UiState.Success("Daily Bonus Claimed! Added +$bonusGained points (₹${bonusGained / 100}) to wallet.")
            } else {
                _uiState.value = UiState.Error("Daily Bonus already claimed! Return in 24 hours.")
            }
        }
    }

    // --- Admin Operations ---
    fun updateLimitSetting(key: String, value: String) {
        viewModelScope.launch {
            repository.updateSetting(key, value)
            _uiState.value = UiState.Success("Config update saved: $key is now set to $value")
        }
    }

    fun addTempMailDomain(domain: String) {
        if (domain.isBlank()) return
        viewModelScope.launch {
            repository.addBannedDomain(domain)
            _uiState.value = UiState.Success("Disposable domain '$domain' added to spam blocker.")
        }
    }

    fun removeTempMailDomain(domain: String) {
        viewModelScope.launch {
            repository.deleteBannedDomain(domain)
            _uiState.value = UiState.Success("Disposable domain '$domain' removed from spam blocker.")
        }
    }

    fun changeUserBanStatus(userEmail: String, ban: Boolean) {
        viewModelScope.launch {
            repository.banUser(userEmail, ban)
            _uiState.value = UiState.Success("User $userEmail status updated: Banned = $ban")
        }
    }

    fun forceReevaluateFraud(userEmail: String) {
        viewModelScope.launch {
            val user = repository.getUser(userEmail)
            if (user != null) {
                val updated = repository.reevaluateFraudState(user, isSubmission = false)
                _uiState.value = UiState.Success("Manual security run complete. Fraud Score: ${updated.fraudScore}%")
                if (userEmail == _currentUser.value?.email) {
                    _currentUser.value = updated
                }
            }
        }
    }

    fun toggleSimulationVpnState(userEmail: String) {
        viewModelScope.launch {
            val user = repository.getUser(userEmail)
            if (user != null) {
                val updated = user.copy(isVpnDetected = !user.isVpnDetected)
                val finalUser = repository.reevaluateFraudState(updated, isSubmission = false)
                _uiState.value = UiState.Success("Simulated VPN status: ${finalUser.isVpnDetected}. Fraud Score: ${finalUser.fraudScore}%")
                if (userEmail == _currentUser.value?.email) {
                    _currentUser.value = finalUser
                }
            }
        }
    }

    fun updateWithdrawalRequest(id: Int, status: String) {
        viewModelScope.launch {
            repository.processWithdrawal(id, status)
            _uiState.value = UiState.Success("Withdrawal record ID $id updated to: $status")
        }
    }

    // --- Moderator Actions ---
    fun loginAsModerator(email: String, passwordPlain: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val mod = repository.getModerator(email)
            if (mod == null) {
                _uiState.value = UiState.Error("Invalid Moderator Email or Password.")
            } else if (mod.password != passwordPlain) {
                _uiState.value = UiState.Error("Invalid Moderator Email or Password.")
            } else if (mod.isSuspended) {
                _uiState.value = UiState.Error("This Moderator account has been suspended by Admin.")
            } else {
                _currentModerator.value = mod
                _uiState.value = UiState.Success("Welcome Back Staff: ${mod.displayName}")
                // Clear active user session
                logout()
                loadAssignedTaskForModerator()
            }
        }
    }

    fun logoutModerator() {
        viewModelScope.launch {
            _currentModerator.value = null
            _assignedTaskForMod.value = null
            _uiState.value = UiState.Idle
        }
    }

    fun createModeratorAccount(email: String, displayName: String, passwordPlain: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val (success, message) = repository.createModerator(email, displayName, passwordPlain)
            if (success) {
                _uiState.value = UiState.Success(message)
            } else {
                _uiState.value = UiState.Error(message)
            }
        }
    }

    fun toggleModeratorSuspension(email: String, isSuspended: Boolean) {
        viewModelScope.launch {
            repository.changeModeratorSuspension(email, isSuspended)
            _uiState.value = UiState.Success("Moderator suspension state updated!")
        }
    }

    fun deleteModeratorAccount(email: String) {
        viewModelScope.launch {
            repository.removeModerator(email)
            _uiState.value = UiState.Success("Moderator account removed permanently!")
        }
    }

    fun loadAssignedTaskForModerator() {
        val mod = _currentModerator.value ?: return
        viewModelScope.launch {
            val task = repository.autoAssignPendingTask(mod.email)
            _assignedTaskForMod.value = task
        }
    }

    fun approveAssignedTask(gmail: String, durationSeconds: Long) {
        val mod = _currentModerator.value ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val (success, message) = repository.approveTask(mod.email, gmail, durationSeconds)
            if (success) {
                _uiState.value = UiState.Success(message)
                _currentModerator.value = repository.getModerator(mod.email)
                loadAssignedTaskForModerator()
            } else {
                _uiState.value = UiState.Error(message)
            }
        }
    }

    fun rejectAssignedTask(gmail: String, reason: String, durationSeconds: Long) {
        val mod = _currentModerator.value ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val (success, message) = repository.rejectTask(mod.email, gmail, reason, durationSeconds)
            if (success) {
                _uiState.value = UiState.Success(message)
                _currentModerator.value = repository.getModerator(mod.email)
                loadAssignedTaskForModerator()
            } else {
                _uiState.value = UiState.Error(message)
            }
        }
    }

    // --- Utilities ---
    fun getDeviceFingerprint(): String {
        return repository.getDeviceFingerprint()
    }

    fun resetUiState() {
        _uiState.value = UiState.Idle
    }

    fun setNetworkStatus(connected: Boolean) {
        _internetConnected.value = connected
    }
}

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val message: String) : UiState
    data class Error(val reason: String) : UiState
}
