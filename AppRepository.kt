package com.example.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class AppRepository(private val context: Context, private val appDao: AppDao) {

    val allSettings: Flow<List<SettingEntity>> = appDao.getAllSettingsFlow()
    val allBannedDomains: Flow<List<BannedDomainEntity>> = appDao.getAllBannedDomainsFlow()
    val allSubmissions: Flow<List<GmailSubmissionEntity>> = appDao.getAllSubmissionsFlow()
    val allWithdrawals: Flow<List<WithdrawalEntity>> = appDao.getAllWithdrawalsFlow()
    val allUsers: Flow<List<UserEntity>> = appDao.getAllUsersFlow()

    init {
        // Run pre-population on a background thread safely
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                prepopulateDefaults()
            } catch (e: Exception) {
                Log.e("AppRepository", "Error during pre-population", e)
            }
        }
    }

    private suspend fun prepopulateDefaults() {
        // Prepopulate reward settings if not configured
        val defaultSettings = mapOf(
            "points_per_submission" to "600",   // 600 points = ₹6 (Since 100 points = ₹1)
            "min_withdrawal_rupees" to "100",  // Minimum ₹100
            "points_per_referral" to "10000",  // 10000 coins as requested
            "mod_payment_per_task" to "3.00"   // Default ₹3.00 reward per verification task
        )
        for ((key, value) in defaultSettings) {
            if (appDao.getSettingValue(key) == null) {
                appDao.insertSetting(SettingEntity(key, value))
            }
        }

        // Seeding a default moderator account for testing convenience
        if (appDao.getModeratorByEmail("mod@verification.com") == null) {
            appDao.insertModerator(
                ModeratorEntity(
                    email = "mod@verification.com",
                    displayName = "Master Verifier",
                    password = "mod123"
                )
            )
        }

        // Prepopulate standard temp-mail domains to protect from cheating immediately
        val testBanned = appDao.getAllBannedDomains()
        if (testBanned.isEmpty()) {
            val domains = listOf(
                "mailinator.com",
                "10minutemail.com",
                "tempmail.com",
                "guerrillamail.com",
                "yopmail.com",
                "trashmail.com",
                "dispostable.com",
                "temp-mail.org",
                "sharklasers.com"
            )
            for (domain in domains) {
                appDao.insertBannedDomain(BannedDomainEntity(domain))
            }
        }
    }

    // --- Authentication ---
    suspend fun getOrCreateUser(email: String, displayName: String, referredByCode: String? = null): UserEntity = withContext(Dispatchers.IO) {
        val existing = appDao.getUserByEmail(email)
        if (existing != null) {
            // Recalculate fraud score on re-login
            val reevaluated = reevaluateFraudState(existing, isSubmission = false)
            return@withContext reevaluated
        }

        // New User -> Create Referral Code and capture Device Fingerprint
        val generatedReferral = email.take(5).uppercase().padEnd(6, '9') + UUID.randomUUID().toString().take(2).uppercase()
        val deviceId = getDeviceFingerprint()

        // Check if device fingerprint is already used by another account (Multi-Account Anti-Spam Check)
        val isFingerprintDuplicated = checkDeviceFingerprintExists(deviceId)

        val newUser = UserEntity(
            email = email,
            displayName = displayName,
            points = 0,
            referralCode = generatedReferral,
            referredBy = if (!isFingerprintDuplicated) referredByCode else null, // Void referral to prevent duplicate device fraud
            deviceFingerprint = deviceId,
            isBanned = false,
            referralEarnings = 0,
            dailyBonusLastClaimed = 0,
            fraudScore = if (isFingerprintDuplicated) 35 else 0,
            fraudFlags = if (isFingerprintDuplicated) "MULTI_ACCOUNT_DETECTION" else "",
            lastSubmissionTime = 0,
            isVpnDetected = false
        )

        appDao.insertUser(newUser)

        // Process referral reward if referrer is valid
        if (referredByCode != null && !isFingerprintDuplicated) {
            applyReferralReward(referredByCode, email)
        }

        val reevaluated = reevaluateFraudState(newUser, isSubmission = false)
        reevaluated
    }

    suspend fun getUser(email: String): UserEntity? = withContext(Dispatchers.IO) {
        appDao.getUserByEmail(email)
    }

    suspend fun banUser(email: String, isBanned: Boolean) = withContext(Dispatchers.IO) {
        val user = appDao.getUserByEmail(email)
        if (user != null) {
            appDao.updateUser(user.copy(isBanned = isBanned))
        }
    }

    // --- Daily Bonus System ---
    suspend fun claimDailyBonus(email: String): Long? = withContext(Dispatchers.IO) {
        val user = appDao.getUserByEmail(email) ?: return@withContext null
        val currentTime = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L
        if (currentTime - user.dailyBonusLastClaimed >= oneDayMillis) {
            val bonusPoints = 20L // Directly 20 coins reward strictly (equal to ₹0.20)
            val updatedUser = user.copy(
                points = user.points + bonusPoints,
                dailyBonusLastClaimed = currentTime
            )
            appDao.updateUser(updatedUser)
            return@withContext bonusPoints
        }
        null
    }

    // --- Device / Anti-Fraud ---
    fun getDeviceFingerprint(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "MOCK_DEVICE_ID"
        } catch (e: Exception) {
            "MOCK_DEVICE_ID"
        }
    }

    private suspend fun checkDeviceFingerprintExists(fingerprint: String): Boolean {
        if (fingerprint == "MOCK_DEVICE_ID") return false
        val allUsersList = appDao.getAllUsers()
        return allUsersList.any { it.deviceFingerprint == fingerprint }
    }

    // Programmatic VPN detection
    fun isRealVpnActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null) {
                        if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                            return true
                        }
                    }
                }
            }
            // Fallback: Check network interface names for TUN/TAP devices
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                for (networkInterface in java.util.Collections.list(interfaces)) {
                    val name = networkInterface.name.lowercase()
                    if (name.contains("tun") || name.contains("tap") || name.contains("p2p") || name.contains("dummy") || name.contains("ppp")) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    // Programmatic Proxy detection
    fun isProxyActive(): Boolean {
        return try {
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            val isSocks = System.getProperty("socksProxyHost")
            (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) || !isSocks.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // Dynamic Fraud Score evaluation (Calculated on Registration, Logins, and Task Submissions)
    suspend fun reevaluateFraudState(
        user: UserEntity,
        isSubmission: Boolean,
        submissionTimestamp: Long = System.currentTimeMillis()
    ): UserEntity = withContext(Dispatchers.IO) {
        var score = 0
        val flags = mutableListOf<String>()

        // 1. Programmatic VPN & Proxy Check
        val vpnDetected = isRealVpnActive() || isProxyActive() || user.isVpnDetected
        if (vpnDetected) {
            score += 45
            flags.add("VPN_OR_PROXY")
        }

        // 2. Multi-Account Device Fingerprint Check
        val fingerprint = user.deviceFingerprint
        if (fingerprint != "MOCK_DEVICE_ID") {
            val allUsers = appDao.getAllUsers()
            val sharingSameDevice = allUsers.filter { it.deviceFingerprint == fingerprint }
            val extraAccounts = sharingSameDevice.size - 1
            if (extraAccounts > 0) {
                // +25 penalty points per duplicate account
                val multiAccountPenalty = (extraAccounts * 25).coerceAtMost(55)
                score += multiAccountPenalty
                flags.add("MULTI_ACCOUNT_DEVICE ($extraAccounts link)")
            }
        }

        // 3. Submissions Rapid Speed Analyzer
        var lastSubTime = user.lastSubmissionTime
        if (isSubmission) {
            if (lastSubTime > 0L) {
                val secElapsed = (submissionTimestamp - lastSubTime) / 1000L
                if (secElapsed < 15L) { // Less than 15 seconds! Heavy spam indicator
                    score += 40
                    flags.add("RAPID_SUBMISSIONS_SPAM")
                } else if (secElapsed < 60L) { // Less than a minute
                    score += 15
                    flags.add("HIGH_FREQUENCY_ACTIVITY")
                }
            }
            lastSubTime = submissionTimestamp
        }

        val finalScore = score.coerceIn(0, 100)
        val flagsStr = flags.joinToString(", ")

        val updated = user.copy(
            fraudScore = finalScore,
            fraudFlags = flagsStr,
            lastSubmissionTime = lastSubTime,
            isVpnDetected = vpnDetected
        )
        appDao.updateUser(updated)
        updated
    }

    // --- Task Submission ---
    suspend fun submitGmailTask(
        userEmail: String,
        submittedGmail: String,
        passwordPlain: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val user = appDao.getUserByEmail(userEmail)
        if (user == null) return@withContext false to "User not found"
        if (user.isBanned) return@withContext false to "Your account is suspended due to security violation."

        // 1. Password Verification (Requirement: ethicbro999)
        if (passwordPlain != "ethicbro999") {
            return@withContext false to "Rejection: Password must match the requested credentials (ethicbro999)"
        }

        // 2. Format verification (Requirement: Valid Gmail)
        val isGmailValid = submittedGmail.endsWith("@gmail.com") && submittedGmail.length > 10
        if (!isGmailValid) {
            return@withContext false to "Rejection: Only standard, validated Gmail accounts are permitted"
        }

        // 3. Domain validation (Requirement: Prevent temporary or disposable domains)
        val parts = submittedGmail.split("@")
        val domain = parts.getOrNull(1)?.lowercase() ?: ""
        val bannedDomainsList = appDao.getAllBannedDomains().map { it.domain.lowercase() }
        if (bannedDomainsList.contains(domain) || domain != "gmail.com") {
            return@withContext false to "Rejection: Temporary, custom or disposable email domains are strictly forbidden"
        }

        // 4. Global Uniqueness validation (Requirement: Globally unique Gmail)
        val existingSubmission = appDao.getSubmissionByGmail(submittedGmail)
        if (existingSubmission != null) {
            return@withContext false to "Rejection: Gmail already registers in our global tracking ledger"
        }

        // 5. Evaluate fraud state during submission (rapid submissions, VPN, proxy, multi-account checks)
        val now = System.currentTimeMillis()
        val fraudCheckedUser = reevaluateFraudState(user, isSubmission = true, submissionTimestamp = now)

        val isHighFraudRisk = fraudCheckedUser.fraudScore >= 75
        val feedbackPrefix = if (isHighFraudRisk) {
            "⚠️ [Fraud Risk: ${fraudCheckedUser.fraudScore}%] "
        } else ""

        // Fetch reward point config
        val rewardPointsStr = appDao.getSettingValue("points_per_submission") ?: "600"
        val rewardPoints = rewardPointsStr.toLongOrNull() ?: 600L

        // Save PENDING Submission for Moderator Verification
        val submission = GmailSubmissionEntity(
            gmail = submittedGmail,
            password = passwordPlain,
            submittedBy = userEmail,
            status = "PENDING",
            timestamp = now
        )
        appDao.insertSubmission(submission)

        // Only save the fraud state (checks, speed analyzer, VPN logic)
        appDao.updateUser(fraudCheckedUser)

        true to "${feedbackPrefix}Task submitted! A moderator will review and approve your points shortly."
    }

    // --- Referral Flow ---
    private suspend fun applyReferralReward(code: String, newcomerEmail: String) {
        val referrer = appDao.getUserByReferralCode(code) ?: return
        
        // Fetch referral incentive from Settings (Configurable: default 10000 coins = ₹10)
        val referralRewardStr = appDao.getSettingValue("points_per_referral") ?: "10000"
        val referralReward = referralRewardStr.toLongOrNull() ?: 10000L

        // Allocate referral cash into withdrawalBalance
        val updatedReferrer = referrer.copy(
            points = referrer.points + referralReward,
            referralEarnings = referrer.referralEarnings + referralReward
        )
        appDao.updateUser(updatedReferrer)
    }

    // --- Withdrawals ---
    suspend fun requestWithdrawal(
        email: String,
        amountRupees: Int,
        upiId: String,
        name: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val user = appDao.getUserByEmail(email) ?: return@withContext false to "Error loading user profile"
        if (user.isBanned) return@withContext false to "Your account is suspended."

        // Load minimum limits
        val minLimitStr = appDao.getSettingValue("min_withdrawal_rupees") ?: "100"
        val minLimit = minLimitStr.toIntOrNull() ?: 100

        // Validation 1: Min Rupee threshold
        if (amountRupees < minLimit) {
            return@withContext false to "Minimum withdrawal limit is ₹$minLimit"
        }

        // Validation 2: Required at least 10 valid Gmail submissions!
        val completedCount = appDao.getCompletedSubmissionsCountByUser(email)
        if (completedCount < 10) {
            return@withContext false to "Withdrawal rejected. You must complete at least 10 valid Gmail submissions (Current: $completedCount/10)"
        }

        // Calculate points dynamic deduction (100 coins = ₹1)
        val pointsRequired = amountRupees * 100L
        if (user.points < pointsRequired) {
            return@withContext false to "Insufficient coins. You require at least $pointsRequired points (₹$amountRupees)"
        }

        // Deduct points and log withdrawal request
        val updatedUser = user.copy(points = user.points - pointsRequired)
        appDao.updateUser(updatedUser)

        val withdrawal = WithdrawalEntity(
            userEmail = email,
            amountRupees = amountRupees,
            pointsDeducted = pointsRequired,
            upiId = upiId,
            name = name,
            status = "PENDING"
        )
        appDao.insertWithdrawal(withdrawal)

        true to "Withdrawal request of ₹$amountRupees lodged successfully! Pending Admin verification."
    }

    suspend fun processWithdrawal(id: Int, status: String) = withContext(Dispatchers.IO) {
        val withdrawal = appDao.getWithdrawalById(id) ?: return@withContext
        if (withdrawal.status != "PENDING") return@withContext

        val updated = withdrawal.copy(status = status)
        appDao.updateWithdrawal(updated)

        // If rejected, return points to user balance
        if (status == "REJECTED") {
            val user = appDao.getUserByEmail(withdrawal.userEmail)
            if (user != null) {
                appDao.updateUser(user.copy(points = user.points + withdrawal.pointsDeducted))
            }
        }
    }

    // --- Admin Control Settings ---
    suspend fun updateSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(SettingEntity(key, value))
    }

    suspend fun addBannedDomain(domain: String) = withContext(Dispatchers.IO) {
        appDao.insertBannedDomain(BannedDomainEntity(domain.lowercase()))
    }

    suspend fun deleteBannedDomain(domain: String) = withContext(Dispatchers.IO) {
        appDao.deleteBannedDomain(domain.lowercase())
    }

    // --- Moderator System ---

    fun getPendingSubmissionsFlow(): Flow<List<GmailSubmissionEntity>> = appDao.getPendingSubmissionsFlow()
    fun getModeratorPaymentsFlow(email: String): Flow<List<ModeratorPaymentEntity>> = appDao.getPaymentsByModeratorFlow(email)
    fun getModeratorLogsFlow(email: String): Flow<List<VerificationLogEntity>> = appDao.getVerificationLogsByModeratorFlow(email)
    fun getAllModeratorsFlow(): Flow<List<ModeratorEntity>> = appDao.getAllModeratorsFlow()
    fun getAllModeratorPaymentsFlow(): Flow<List<ModeratorPaymentEntity>> = appDao.getAllModeratorPaymentsFlow()
    fun getAllVerificationLogsFlow(): Flow<List<VerificationLogEntity>> = appDao.getAllVerificationLogsFlow()

    suspend fun getModerator(email: String): ModeratorEntity? = withContext(Dispatchers.IO) {
        appDao.getModeratorByEmail(email)
    }

    suspend fun createModerator(email: String, displayName: String, passwordPlain: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (email.isBlank() || displayName.isBlank() || passwordPlain.isBlank()) {
            return@withContext false to "All fields must be filled"
        }
        val existing = appDao.getModeratorByEmail(email)
        if (existing != null) {
            return@withContext false to "A moderator with this email already exists"
        }
        val newMod = ModeratorEntity(
            email = email,
            displayName = displayName,
            password = passwordPlain
        )
        appDao.insertModerator(newMod)
        true to "Moderator account created successfully!"
    }

    suspend fun changeModeratorSuspension(email: String, isSuspended: Boolean) = withContext(Dispatchers.IO) {
        val mod = appDao.getModeratorByEmail(email) ?: return@withContext
        appDao.updateModerator(mod.copy(isSuspended = isSuspended))
    }

    suspend fun removeModerator(email: String) = withContext(Dispatchers.IO) {
        appDao.deleteModerator(email)
    }

    // Task Management: Auto-assign tasks to prevent double checking + Timer/Lock system
    suspend fun autoAssignPendingTask(moderatorEmail: String): GmailSubmissionEntity? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        appDao.clearExpiredAssignments(now)

        // 1. Check if this moderator already has an active locked assignment
        val activeLock = appDao.getActiveAssignmentForModerator(moderatorEmail)
        if (activeLock != null) {
            val lockedTask = appDao.getSubmissionByGmail(activeLock.submissionGmail)
            if (lockedTask != null && lockedTask.status == "PENDING") {
                return@withContext lockedTask
            } else {
                appDao.deleteAssignedTask(activeLock.submissionGmail)
            }
        }

        // 2. Query all pending tasks
        val pendingTasks = appDao.getPendingSubmissions()
        if (pendingTasks.isEmpty()) return@withContext null

        // 3. Query all currently held locks by other moderators
        val activeLocks = appDao.getAllActiveAssignedTasks()
        val lockedGmails = activeLocks.map { it.submissionGmail }.toSet()

        // 4. Find the first pending task which is NOT currently locked by any other moderator
        val assignable = pendingTasks.find { !lockedGmails.contains(it.gmail) }
        if (assignable != null) {
            val lockExpiry = now + (120 * 1000L) // 2-minute lock timer
            val lock = AssignedTaskEntity(
                submissionGmail = assignable.gmail,
                moderatorEmail = moderatorEmail,
                assignedTime = now,
                expiryTime = lockExpiry,
                status = "LOCKED"
            )
            appDao.insertAssignedTask(lock)
            return@withContext assignable
        }

        null
    }

    suspend fun approveTask(moderatorEmail: String, gmail: String, durationSeconds: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val task = appDao.getSubmissionByGmail(gmail) ?: return@withContext false to "Task not found"
        if (task.status != "PENDING") return@withContext false to "Task already verified as ${task.status}"

        val moderator = appDao.getModeratorByEmail(moderatorEmail)
        if (moderator == null || moderator.isSuspended) {
            return@withContext false to "Access denied or Moderator account suspended"
        }

        // Apply Approved State
        val approvedTask = task.copy(status = "COMPLETED")
        appDao.updateSubmission(approvedTask)

        // Reward User
        val submitter = appDao.getUserByEmail(task.submittedBy)
        if (submitter != null) {
            val rewardPointsStr = appDao.getSettingValue("points_per_submission") ?: "600"
            val rewardPoints = rewardPointsStr.toLongOrNull() ?: 600L
            val updatedUser = submitter.copy(
                points = submitter.points + rewardPoints,
                submissionsCount = submitter.submissionsCount + 1
            )
            appDao.updateUser(updatedUser)
        }

        // Reward Moderator (Fixed payment per verified task)
        val modPaymentStr = appDao.getSettingValue("mod_payment_per_task") ?: "3.00"
        val modPayment = modPaymentStr.toDoubleOrNull() ?: 3.00
        val updatedMod = moderator.copy(
            verifiedCount = moderator.verifiedCount + 1,
            approvalCount = moderator.approvalCount + 1,
            totalSpeedSec = moderator.totalSpeedSec + durationSeconds,
            earningsRupees = moderator.earningsRupees + modPayment
        )
        appDao.updateModerator(updatedMod)

        // Log Verification and Reward
        appDao.insertVerificationLog(
            VerificationLogEntity(
                moderatorEmail = moderatorEmail,
                submissionGmail = gmail,
                action = "APPROVE",
                rejectionReason = null,
                durationSeconds = durationSeconds
            )
        )
        appDao.insertModeratorPayment(
            ModeratorPaymentEntity(
                moderatorEmail = moderatorEmail,
                amount = modPayment,
                type = "REWARD",
                status = "APPROVED"
            )
        )

        // Delete Lock Assignment
        appDao.deleteAssignedTask(gmail)

        true to "Task Approved! Reward added to user and moderator wallet."
    }

    suspend fun rejectTask(moderatorEmail: String, gmail: String, reason: String, durationSeconds: Long): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val task = appDao.getSubmissionByGmail(gmail) ?: return@withContext false to "Task not found"
        if (task.status != "PENDING") return@withContext false to "Task already verified as ${task.status}"

        val moderator = appDao.getModeratorByEmail(moderatorEmail)
        if (moderator == null || moderator.isSuspended) {
            return@withContext false to "Access denied or Moderator account suspended"
        }

        if (reason.isBlank()) {
            return@withContext false to "Rejection reason is mandatory."
        }

        // Apply Rejected State
        val rejectedTask = task.copy(status = "REJECTED", rejectionReason = reason)
        appDao.updateSubmission(rejectedTask)

        // Reward Moderator (Fixed payment for verifying/doing the work)
        val modPaymentStr = appDao.getSettingValue("mod_payment_per_task") ?: "3.00"
        val modPayment = modPaymentStr.toDoubleOrNull() ?: 3.00
        val updatedMod = moderator.copy(
            verifiedCount = moderator.verifiedCount + 1,
            rejectionCount = moderator.rejectionCount + 1,
            totalSpeedSec = moderator.totalSpeedSec + durationSeconds,
            earningsRupees = moderator.earningsRupees + modPayment
        )
        appDao.updateModerator(updatedMod)

        // Log Verification and Reward
        appDao.insertVerificationLog(
            VerificationLogEntity(
                moderatorEmail = moderatorEmail,
                submissionGmail = gmail,
                action = "REJECT",
                rejectionReason = reason,
                durationSeconds = durationSeconds
            )
        )
        appDao.insertModeratorPayment(
            ModeratorPaymentEntity(
                moderatorEmail = moderatorEmail,
                amount = modPayment,
                type = "REWARD",
                status = "APPROVED"
            )
        )

        // Delete Lock Assignment
        appDao.deleteAssignedTask(gmail)

        true to "Task Rejected with reason: $reason."
    }

    suspend fun requestModeratorWithdrawal(moderatorEmail: String, amount: Double, upiId: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val moderator = appDao.getModeratorByEmail(moderatorEmail) ?: return@withContext false to "Moderator not found"
        if (moderator.isSuspended) return@withContext false to "Account suspended."
        if (amount <= 0) return@withContext false to "Invalid withdrawal amount."
        if (amount > moderator.earningsRupees) {
            return@withContext false to "Insufficient earnings balance."
        }
        if (upiId.isBlank()) return@withContext false to "UPI ID is mandatory"

        // Deduct balance and insert pending withdrawal request
        appDao.updateModerator(moderator.copy(earningsRupees = moderator.earningsRupees - amount))
        appDao.insertModeratorPayment(
            ModeratorPaymentEntity(
                moderatorEmail = moderatorEmail,
                amount = amount,
                type = "WITHDRAWAL",
                upiId = upiId,
                status = "PENDING"
            )
        )

        true to "Moderator withdrawal request logged successfully. Pending Admin payout."
    }

    suspend fun processModeratorWithdrawal(id: Int, status: String) = withContext(Dispatchers.IO) {
        val payment = appDao.getModeratorPaymentById(id) ?: return@withContext
        if (payment.type != "WITHDRAWAL" || payment.status != "PENDING") return@withContext

        // Update Payout Status
        appDao.updateModeratorPayment(payment.copy(status = status))

        // Revert Balance if Rejected
        if (status == "REJECTED") {
            val mod = appDao.getModeratorByEmail(payment.moderatorEmail)
            if (mod != null) {
                appDao.updateModerator(mod.copy(earningsRupees = mod.earningsRupees + payment.amount))
            }
        }
    }
}
