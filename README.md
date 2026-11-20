# AutoTPA (Fabric, Minecraft 1.21.4)

Mod klienta, który sam wysyła `/tpaccept`, gdy dostaniesz prośbę o teleportację.

## Sterowanie
- **GUI** – klawisz `O` albo `/autotpa gui`: przełącznik ON/OFF, komenda akceptująca, delay,
  whitelista i własny tekst prośby
- **ON/OFF** – klawisz `K` albo `/autotpa` (przełącza), `/autotpa on`, `/autotpa off`
- `/autotpa status` – aktualne ustawienia
- `/autotpa delay <ticki>` – opóźnienie przed akceptacją (20 ticków = 1 s)
- `/autotpa command <komenda>` – inna komenda niż `tpaccept` (np. `tpyes`)
- `/autotpa text <fragment>` – własny fragment wiadomości, po którym mod rozpozna prośbę
- `/autotpa whitelist add|remove|list|clear [nick]` – akceptuj tylko wybranych graczy
- `/autotpa debug` – wypisuje na czacie każdą wiadomość z serwera (do diagnozy)

## Jak mod rozpoznaje prośbę
Mod reaguje, gdy wiadomość z serwera zawiera: komendę akceptującą z ukośnikiem (np. „wpisz /tpaccept”),
Twój własny tekst z GUI albo jeden ze wzorców z `config/autotpa.json`. Domyślnie mod jest **wyłączony**,
więc włącz go przyciskiem w GUI lub klawiszem `K`.

Jeśli nic się nie dzieje: włącz `/autotpa debug`, poproś kogoś o tpa i zobacz, jak dokładnie wygląda
wiadomość serwera. Wpisz jej fragment w GUI w polu „Własny tekst prośby”.

## Budowanie
Wrzuć projekt na GitHub – workflow `.github/workflows/build.yml` zbuduje jar (zakładka Actions →
Artifacts). Lokalnie: JDK 21 i `./gradlew build`, jar w `build/libs/`.

## Uwaga
Część serwerów zakazuje automatyzacji w regulaminie – sprawdź zasady przed użyciem.
