package io.github.gitjacobbit.whoscalling.domain

enum class Verdict { ANSWER, CAUTION, REJECT, UNKNOWN }

enum class CallerCategory {
    TELEMARKETING, SCAM, COURIER, BANK, COMPANY, INSTITUTION, PRIVATE, UNKNOWN;

    val label: String
        get() = when (this) {
            TELEMARKETING -> "Telemarketing"
            SCAM -> "Oszustwo"
            COURIER -> "Kurier"
            BANK -> "Bank"
            COMPANY -> "Firma"
            INSTITUTION -> "Instytucja"
            PRIVATE -> "Prywatny"
            UNKNOWN -> "Nieznany"
        }
}

enum class UserOverride { SAFE, SPAM }
