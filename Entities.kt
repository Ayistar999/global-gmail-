package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val email: String,
    val displayName: String,
    val points: Long = 0,
    val referralCode: String,
    val referredBy: String? = null,
    val isBanned: Boolean = false,
    val deviceFingerprint: String,
    val submissionsCount: Int = 0,
    val referralEarnings: Long = 0,
    val dailyBonusLastClaimed: Long = 0,
    val fraudScore: Int = 0,
    val fraudFlags: String = "",
    val lastSubmissionTime: Long = 0L,
    val isVpnDetected: Boolean = false
)

@Entity(tableName = "gmail_submissions")
data class GmailSubmissionEntity(
    @PrimaryKey val gmail: String, // Globally unique constraint
    val password: String,
    val submittedBy: String,
    val status: String, // "PENDING", "COMPLETED", "REJECTED"
    val rejectionReason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "withdrawals")
data class WithdrawalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val amountRupees: Int,
    val pointsDeducted: Long,
    val upiId: String,
    val name: String,
    val status: String, // "PENDING", "APPROVED", "REJECTED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "banned_domains")
data class BannedDomainEntity(
    @PrimaryKey val domain: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "moderators")
data class ModeratorEntity(
    @PrimaryKey val email: String,
    val displayName: String,
    val password: String,
    val isSuspended: Boolean = false,
    val verifiedCount: Int = 0,
    val approvalCount: Int = 0,
    val rejectionCount: Int = 0,
    val totalSpeedSec: Long = 0L,
    val earningsRupees: Double = 0.0
)

@Entity(tableName = "verification_logs")
data class VerificationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val moderatorEmail: String,
    val submissionGmail: String,
    val action: String, // "APPROVE", "REJECT"
    val rejectionReason: String?,
    val durationSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "moderator_payments")
data class ModeratorPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val moderatorEmail: String,
    val amount: Double,
    val type: String, // "REWARD" or "WITHDRAWAL"
    val upiId: String? = null,
    val status: String, // "APPROVED", "PENDING", "REJECTED"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "assigned_tasks")
data class AssignedTaskEntity(
    @PrimaryKey val submissionGmail: String, // Ensure a submission can only have one active lock
    val moderatorEmail: String,
    val assignedTime: Long,
    val expiryTime: Long,
    val status: String // "LOCKED", "COMPLETED", "EXPIRED"
)
