package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- Users ---
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE referralCode = :code LIMIT 1")
    suspend fun getUserByReferralCode(code: String): UserEntity?

    @Query("SELECT * FROM users ORDER BY points DESC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)


    // --- Gmail Submissions ---
    @Query("SELECT * FROM gmail_submissions ORDER BY timestamp DESC")
    fun getAllSubmissionsFlow(): Flow<List<GmailSubmissionEntity>>

    @Query("SELECT * FROM gmail_submissions WHERE gmail = :gmail LIMIT 1")
    suspend fun getSubmissionByGmail(gmail: String): GmailSubmissionEntity?

    @Query("SELECT * FROM gmail_submissions WHERE submittedBy = :email")
    fun getSubmissionsByUserFlow(email: String): Flow<List<GmailSubmissionEntity>>

    @Query("SELECT COUNT(*) FROM gmail_submissions WHERE submittedBy = :email AND status = 'COMPLETED'")
    suspend fun getCompletedSubmissionsCountByUser(email: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSubmission(submission: GmailSubmissionEntity): Long

    @Update
    suspend fun updateSubmission(submission: GmailSubmissionEntity)


    // --- Withdrawals ---
    @Query("SELECT * FROM withdrawals ORDER BY timestamp DESC")
    fun getAllWithdrawalsFlow(): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE userEmail = :email ORDER BY timestamp DESC")
    fun getWithdrawalsByUserFlow(email: String): Flow<List<WithdrawalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity)

    @Query("SELECT * FROM withdrawals WHERE id = :id LIMIT 1")
    suspend fun getWithdrawalById(id: Int): WithdrawalEntity?

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)


    // --- Banned Domains ---
    @Query("SELECT * FROM banned_domains")
    fun getAllBannedDomainsFlow(): Flow<List<BannedDomainEntity>>

    @Query("SELECT * FROM banned_domains")
    suspend fun getAllBannedDomains(): List<BannedDomainEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBannedDomain(domain: BannedDomainEntity)

    @Query("DELETE FROM banned_domains WHERE domain = :domain")
    suspend fun deleteBannedDomain(domain: String)


    // --- Settings ---
    @Query("SELECT * FROM settings")
    fun getAllSettingsFlow(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)

    // --- Moderators ---
    @Query("SELECT * FROM moderators WHERE email = :email LIMIT 1")
    suspend fun getModeratorByEmail(email: String): ModeratorEntity?

    @Query("SELECT * FROM moderators ORDER BY displayName ASC")
    fun getAllModeratorsFlow(): Flow<List<ModeratorEntity>>

    @Query("SELECT * FROM moderators")
    suspend fun getAllModerators(): List<ModeratorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModerator(moderator: ModeratorEntity)

    @Update
    suspend fun updateModerator(moderator: ModeratorEntity)

    @Query("DELETE FROM moderators WHERE email = :email")
    suspend fun deleteModerator(email: String)


    // --- Verification Logs ---
    @Query("SELECT * FROM verification_logs ORDER BY timestamp DESC")
    fun getAllVerificationLogsFlow(): Flow<List<VerificationLogEntity>>

    @Query("SELECT * FROM verification_logs WHERE moderatorEmail = :email ORDER BY timestamp DESC")
    fun getVerificationLogsByModeratorFlow(email: String): Flow<List<VerificationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVerificationLog(log: VerificationLogEntity)


    // --- Moderator Payments ---
    @Query("SELECT * FROM moderator_payments ORDER BY timestamp DESC")
    fun getAllModeratorPaymentsFlow(): Flow<List<ModeratorPaymentEntity>>

    @Query("SELECT * FROM moderator_payments WHERE moderatorEmail = :email ORDER BY timestamp DESC")
    fun getPaymentsByModeratorFlow(email: String): Flow<List<ModeratorPaymentEntity>>

    @Query("SELECT * FROM moderator_payments WHERE id = :id LIMIT 1")
    suspend fun getModeratorPaymentById(id: Int): ModeratorPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModeratorPayment(payment: ModeratorPaymentEntity)

    @Update
    suspend fun updateModeratorPayment(payment: ModeratorPaymentEntity)


    // --- Assigned Tasks (Locks) ---
    @Query("SELECT * FROM assigned_tasks WHERE submissionGmail = :gmail LIMIT 1")
    suspend fun getAssignedTaskByGmail(gmail: String): AssignedTaskEntity?

    @Query("SELECT * FROM assigned_tasks WHERE moderatorEmail = :email AND status = 'LOCKED' LIMIT 1")
    suspend fun getActiveAssignmentForModerator(email: String): AssignedTaskEntity?

    @Query("SELECT * FROM assigned_tasks WHERE status = 'LOCKED'")
    suspend fun getAllActiveAssignedTasks(): List<AssignedTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssignedTask(task: AssignedTaskEntity)

    @Query("DELETE FROM assigned_tasks WHERE submissionGmail = :gmail")
    suspend fun deleteAssignedTask(gmail: String)

    @Query("DELETE FROM assigned_tasks WHERE expiryTime < :now AND status = 'LOCKED'")
    suspend fun clearExpiredAssignments(now: Long)


    // --- Gmail Submissions Additions ---
    @Query("SELECT * FROM gmail_submissions WHERE status = 'PENDING'")
    suspend fun getPendingSubmissions(): List<GmailSubmissionEntity>

    @Query("SELECT * FROM gmail_submissions WHERE status = 'PENDING' ORDER BY timestamp ASC")
    fun getPendingSubmissionsFlow(): Flow<List<GmailSubmissionEntity>>
}
