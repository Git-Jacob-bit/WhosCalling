package io.github.gitjacobbit.whoscalling.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class CallWithNumber(
    val id: Long,
    val e164: String,
    val timestamp: Long,
    val blocked: Boolean,
    val verdict: String?,
    val score: Int?,
    val category: String?,
    val owner: String?,
    val userOverride: String?,
)

@Dao
interface NumberDao {
    @Query("SELECT * FROM numbers WHERE e164 = :e164")
    suspend fun get(e164: String): PhoneNumberEntity?

    @Query("SELECT * FROM numbers WHERE e164 = :e164")
    fun observe(e164: String): Flow<PhoneNumberEntity?>

    @Upsert
    suspend fun upsert(entity: PhoneNumberEntity)

    @Query("UPDATE numbers SET callCount = callCount + 1, lastCallAt = :at WHERE e164 = :e164")
    suspend fun registerCall(e164: String, at: Long)

    @Query("UPDATE numbers SET userOverride = :override WHERE e164 = :e164")
    suspend fun setOverride(e164: String, override: String?)

    @Insert
    suspend fun insertCall(call: CallLogEntity)

    @Query(
        """
        SELECT c.id, c.e164, c.timestamp, c.blocked, n.verdict, n.score, n.category, n.owner, n.userOverride
        FROM calls c LEFT JOIN numbers n ON n.e164 = c.e164
        ORDER BY c.timestamp DESC
        """
    )
    fun observeHistory(): Flow<List<CallWithNumber>>

    @Query("SELECT * FROM numbers ORDER BY checkedAt DESC")
    suspend fun allNumbers(): List<PhoneNumberEntity>

    @Query("SELECT COUNT(*) FROM calls WHERE e164 = :e164")
    suspend fun countCalls(e164: String): Int

    @Query("SELECT MAX(timestamp) FROM calls WHERE e164 = :e164")
    suspend fun lastCallAt(e164: String): Long?

    @Query("SELECT * FROM calls WHERE e164 = :e164 ORDER BY timestamp DESC")
    fun observeCallsFor(e164: String): Flow<List<CallLogEntity>>
}
