# WhosCalling

Aplikacja na Androida, która podczas dzwonienia z nieznanego numeru pokazuje nakładkę z oceną AI: kto dzwoni i czy warto odebrać.

- **Wykrywanie połączeń** przez systemowe `CallScreeningService`. System wywołuje je tylko dla numerów spoza kontaktów.
- **Nakładka** nad ekranem połączenia: werdykt, poziom ryzyka, kategoria, krótkie podsumowanie. Można ją przesuwać, zwijać i oznaczyć numer jako spam lub bezpieczny.
- **AI**: OpenAI Responses API z wyszukiwaniem w sieci i odpowiedzią w ustalonym schemacie JSON.
- **Lokalna baza (Room)**: każdy wynik zostaje na telefonie, więc przy kolejnym połączeniu werdykt pojawia się od razu. Twoja ocena zawsze ma pierwszeństwo przed oceną AI.
- **Analiza offline** (libphonenumber): kraj, typ numeru, prefiksy typowe dla oszustw wangiri. Działa bez klucza API.
- **Automatyczne blokowanie** numerów oznaczonych jako spam i (opcjonalnie) numerów z wysokim ryzykiem.

## Klucz API

Każdy użytkownik podaje **własny** klucz OpenAI w onboardingu. Aplikacja nie zawiera żadnego klucza i nie ma backendu. Klucz jest szyfrowany kluczem z Android Keystore. Numer dzwoniącego trafia wyłącznie do OpenAI, a koszt zapytań pokrywa właściciel klucza.

Domyślny model to `gpt-5-mini`. Można go zmienić w ustawieniach, ale model musi obsługiwać narzędzie `web_search`.

## Instalacja

Pobierz APK z [Releases](../../releases), zainstaluj i przejdź onboarding. Wymagane uprawnienia:

1. Rola aplikacji do identyfikacji połączeń.
2. Wyświetlanie nad innymi aplikacjami.
3. (Zalecane) Stan telefonu i powiadomienia: nakładka sama chowa się po zakończeniu rozmowy.
4. (Zalecane) Wyłączenie optymalizacji baterii. Na Xiaomi, Huawei i Samsungu warto też włączyć autostart lub „bez ograniczeń”.

Wymagany Android 10 lub nowszy.

## Budowanie

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew testDebugUnitTest lintDebug
```

## Wydawanie wersji

Release buduje się automatycznie po wypchnięciu taga `vX.Y.Z` (`.github/workflows/release.yml`). Jednorazowa konfiguracja:

```bash
keytool -genkeypair -v -keystore whoscalling-release.jks -alias whoscalling \
  -keyalg RSA -keysize 4096 -validity 10000

gh secret set SIGNING_KEYSTORE_BASE64 < <(base64 -w0 whoscalling-release.jks)
gh secret set SIGNING_STORE_PASSWORD
gh secret set SIGNING_KEY_ALIAS --body whoscalling
gh secret set SIGNING_KEY_PASSWORD
```

**Zrób kopię zapasową pliku `.jks` i haseł.** Bez nich nie da się wydać aktualizacji, którą zainstalowane aplikacje przyjmą.

Potem:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Lokalny podpisany build: utwórz `keystore.properties` (jest w `.gitignore`) z kluczami `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Architektura

```
call/      CallScreeningService, sesja połączenia, foreground service ze stanem rozmowy
overlay/   okno nakładki (WindowManager + Compose) i jej UI
domain/    analiza lokalna, repozytorium (cache → AI → zapis)
data/      Room, DataStore + Keystore, klient OpenAI, sprawdzanie aktualizacji
ui/        onboarding, historia, szczegóły numeru, ustawienia
```
