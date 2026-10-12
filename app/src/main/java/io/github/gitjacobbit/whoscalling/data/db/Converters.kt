package io.github.gitjacobbit.whoscalling.data.db

import androidx.room.TypeConverter
import io.github.gitjacobbit.whoscalling.domain.CallerCategory
import io.github.gitjacobbit.whoscalling.domain.UserOverride
import io.github.gitjacobbit.whoscalling.domain.Verdict
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter fun fromVerdict(v: Verdict): String = v.name
    @TypeConverter fun toVerdict(s: String): Verdict = Verdict.valueOf(s)

    @TypeConverter fun fromCategory(c: CallerCategory): String = c.name
    @TypeConverter fun toCategory(s: String): CallerCategory = CallerCategory.valueOf(s)

    @TypeConverter fun fromOverride(o: UserOverride?): String? = o?.name
    @TypeConverter fun toOverride(s: String?): UserOverride? = s?.let(UserOverride::valueOf)

    @TypeConverter fun fromList(list: List<String>): String = Json.encodeToString(list)
    @TypeConverter fun toList(s: String): List<String> = Json.decodeFromString(s)
}
