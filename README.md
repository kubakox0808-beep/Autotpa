# AutoTPA (Fabric, Minecraft 1.21.4)

Mod klienta, który sam wysyła `/tpaccept`, gdy dostaniesz prośbę o teleportację.

## Sterowanie
- **GUI** – klawisz `O` albo `/autotpa gui` (przełącznik ON/OFF, opóźnienie, whitelista)
- **ON/OFF** – klawisz `K` albo `/autotpa` (przełącza), `/autotpa on`, `/autotpa off`
- `/autotpa status` – aktualne ustawienia
- `/autotpa delay <ticki>` – opóźnienie przed akceptacją (20 ticków = 1 s)
- `/autotpa command <komenda>` – inna komenda niż `tpaccept` (np. `tpyes`)
- `/autotpa whitelist add|remove|list|clear [nick]` – akceptuj tylko wybranych graczy

Klawisze zmienisz w *Opcje → Sterowanie → AutoTPA*.

## Konfiguracja
Plik `config/autotpa.json`. Pole `patterns` to lista wyrażeń (regex), po których mod rozpoznaje
prośbę o tpa. Jeśli Twój serwer pisze inaczej, dopisz tam fragment jego wiadomości.

## Budowanie
1. Zainstaluj JDK 21.
2. Najprościej: pobierz szablon z https://fabricmc.net/develop/template/ (dla 1.21.4), a potem
   podmień w nim `src/`, `build.gradle`, `gradle.properties` plikami z tego folderu
   (dzięki temu masz gotowego `gradlew`).
3. `./gradlew build` → gotowy jar w `build/libs/autotpa-1.0.0.jar`.
4. Wrzuć jar do `.minecraft/mods` razem z Fabric API i Fabric Loaderem.

## Uwaga
Część serwerów zakazuje automatyzacji w regulaminie – sprawdź zasady przed użyciem.
