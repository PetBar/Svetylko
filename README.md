# Světýlko – notifikační LED pro Android

Jednoduchá náhrada chybějící notifikační diody. Když přijde oznámení a displej je zhasnutý,
rozsvítí se černá obrazovka se světýlkem v barvě aplikace: kroužek kolem průstřelu kamery
nebo malá tečka jako stará LED dioda. Při nabíjení svítí samostatná tečka, jejíž barva ukazuje
stav baterie.

Aplikace je celá v češtině, nemá reklamy, nesbírá data a **nemá ani oprávnění k internetu**.

**[⬇ Stáhnout Svetylko-1.2.apk](https://github.com/PetBar/svetylko/releases/latest)**

## Funkce

**Světýlko oznámení**
- kroužek kolem kamery, LED tečka (vlevo / uprostřed / vpravo) nebo obojí
- barva podle aplikace: automaticky z oznámení nebo z ikony, případně vlastní barva
- výběr aplikací, které svítí (tlačítka *Označit vše* / *Odznačit vše* a hledání)
- animace: pulzování, bliknutí nebo stálé svícení
- nastavitelný jas, velikost a maximální doba svícení
- tichá a trvalá oznámení (hudba, navigace) nesvítí

**Tečka nabíjení**
- 🔴 červená: nabíjí se, pod 80 %
- 🔵 modrá: 80–99 %
- 🟢 zelená: nabito 100 % (nebo když ochrana baterie zastaví nabíjení)
- vlastní poloha (vlevo / vpravo), jas, velikost a animace (dýchání / bliknutí / stálé)
- volitelně procenta baterie vedle tečky
- svítí i zároveň se světýlkem oznámení

**Ostatní**
- režim Nerušit: nesvítí nic / svítí jen nabíjení / svítí vše
- klepnutím světýlko zhasneš, odemčením telefonu zmizí
- v kapse se displej vypne díky senzoru přiblížení
- ochrana proti vypálení displeje (světlo se nepatrně posouvá)

## Podporované telefony

Android 11 a novější. Vyzkoušeno na:
- Samsung Galaxy A35 5G (One UI 8.5, Android 16)
- Xiaomi Redmi Note 14 (HyperOS 3, Android 16)

## Instalace

1. Stáhni `Svetylko-1.2.apk` z [Releases](https://github.com/PetBar/svetylko/releases/latest) přímo v telefonu.
2. **Samsung:** dočasně vypni *Nastavení → Zabezpečení a soukromí → Automatický blokovač*.
3. Otevři stažený soubor a povol instalaci z neznámého zdroje.
4. Pokud se ozve **Google Play Protect**, zvol *Další podrobnosti → Přesto nainstalovat*.
   Varování se objeví jen proto, že aplikace nepochází z Obchodu Play.
5. Spusť Světýlko a u každého červeného ✗ klepni na **Povolit**:
   - **Přístup k oznámením.** Je-li přepínač šedý („omezené nastavení“): *Informace o aplikaci → ⋮ →
     Povolit omezená nastavení*, pak to zkus znovu.
   - **Oznámení této aplikace**
   - **Celoobrazovková oznámení:** díky nim se rozsvítí displej
   - **Baterie bez omezení**
   - **Xiaomi navíc:** *Zobrazit na zamčené obrazovce* a *Autostart*
6. Klepni na **Vyzkoušet světýlko**.

Novou verzi nainstaluješ přes starou, nastavení zůstane.

## Spotřeba baterie

Když světýlko svítí, displej je zapnutý na velmi nízký jas a svítí jen pár pixelů.
Spotřeba je zhruba 2–3 % za hodinu. Proto má světýlko oznámení limit „Svítit nejdéle“
(výchozí 30 minut). Tečka nabíjení svítí jen na nabíječce.

## Sestavení ze zdrojového kódu

Projekt nepotřebuje Gradle ani Android Studio, stačí JDK a nástroje z Android SDK
(`aapt2`, `android.jar`, `d8`, `apksigner`). Postup a proměnné jsou popsané v [`build.sh`](build.sh).

```
src/cz/svetylko/app/
  NotifService.java   – sleduje oznámení, displej a nabíjení
  LightActivity.java  – černá obrazovka přes zamčený displej
  LightView.java      – kreslení kroužku a teček
  MainActivity.java   – nastavení
  Prefs.java, ColorUtil.java
```

## Licence

[MIT](LICENSE)
