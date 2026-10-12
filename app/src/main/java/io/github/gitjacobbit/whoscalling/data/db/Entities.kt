package io.github.gitjacobbit.whoscalling.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.gitjacobbit.whoscalling.domain.CallerCategory
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.domain.Verdict

@Entity(tableName = "numbers")
data class PhoneNumberEntity(
    @PrimaryKey val e164: String,
    val verdict: Verdict,
    val score: Int,
    val category: CallerCategory,
    val owner: String?,
    val summary: String,
    val sources: List<String>,
    val checkedAt: Long,
    val aiChecked: Boolean,
    val callCount: Int = 0,
    val lastCallAt: Long? = null,
    val userOverride: UserOverride? = null,
    val userNote: String? = null,
)

@Entity(tableName = "calls", indices = [Index("e164")])
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val e164: String,
    val timestamp: Long,
    val blocked: Boolean,
)
