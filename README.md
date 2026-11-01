# Světýlko – notifikační LED pro Android

Jednoduchá náhrada chybějící notifikační diody. Když přijde oznámení a displej je zhasnutý,
rozsvítí se černá obrazovka se světýlkem v barvě aplikace: kroužek kolem průstřelu kamery
nebo malá tečka jako stará LED dioda. Při nabíjení svítí samostatná tečka, jejíž barva ukazuje
stav baterie.

Aplikace je celá v češtině, nemá reklamy, nesbírá data a **nemá ani oprávnění k internetu**.

**[⬇ Stáhnout Svetylko-1.4.apk](https://github.com/PetBar/svetylko/raw/main/apk/Svetylko-1.4.apk)**

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
- tlačítko *Zkouška se zhasnutým displejem*: ověří, jestli systém světýlko pustí
- záznam pro řešení potíží: zobrazit, odeslat, smazat (bez obsahu zpráv)
- klepnutím světýlko zhasneš, odemčením telefonu zmizí
- v kapse se displej vypne díky senzoru přiblížení
- ochrana proti vypálení displeje (světlo se nepatrně posouvá)

## Podporované telefony

Android 11 a novější. Vyzkoušeno na:
- Samsung Galaxy A35 5G (One UI 8.5, Android 16)
- Xiaomi Redmi Note 14 (HyperOS 3, Android 16)

## Instalace

1. Otevři v telefonu odkaz [Svetylko-1.4.apk](https://github.com/PetBar/svetylko/raw/main/apk/Svetylko-1.4.apk) a soubor stáhni.
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
6. **Xiaomi:** nastav ještě všechno z části [Xiaomi / HyperOS](#xiaomi--hyperos).
7. Klepni na **Vyzkoušet světýlko** a pak na **Zkouška se zhasnutým displejem**
   (hned zhasni displej, za 10 s se má světýlko rozsvítit).

Novou verzi nainstaluješ přes starou, nastavení zůstane.

## Xiaomi / HyperOS

HyperOS má vlastní oprávnění a vlastní spořič baterie. Aplikace je nevidí, takže v ní můžou
svítit samé zelené fajfky, a přesto ji systém blokuje. V aplikaci jsou pro Xiaomi tři řádky,
které otevřou správná místa. Ručně je najdeš v *Nastavení → Aplikace → Spravovat aplikace → Světýlko*:

- **Autostart:** zapnout
- **Spořič baterie:** *Bez omezení*
- **Další oprávnění:**
  - *Zobrazit na zamčené obrazovce:* povolit
  - *Zobrazovat vyskakovací okna při běhu na pozadí:* povolit (bez toho se světýlko nespustí)
  - *Zobrazovat vyskakovací okna / Plovoucí okna:* povolit, pokud tam jsou
- **Oznámení:** zapnout *Plovoucí oznámení* a *Oznámení na zamčené obrazovce*,
  totéž i u kategorie **„Rozsvícení světýlka“**
- v přehledu spuštěných aplikací podržet Světýlko a **zamknout** ho (zámeček)

Když to pořád nejde: *Záznam pro řešení potíží → Smazat*, pak *Zkouška se zhasnutým displejem*,
pošli si zprávu se zhasnutým displejem a *Záznam → Zobrazit*. Ze záznamu je vidět, jestli
aplikace oznámení zachytila a jestli ji systém nechal rozsvítit displej.

## Historie verzí

- **1.4:** černá úvodní obrazovka, displej při rozsvícení bíle neproblikne
- **1.3:** zkouška se zhasnutým displejem, záznam pro řešení potíží, pojistka a nastavení pro Xiaomi
- **1.2:** samostatná tečka nabíjení (červená / modrá / zelená), procenta baterie, tři režimy pro Nerušit
- **1.1:** signalizace nabíjení, hromadný výběr aplikací a hledání
- **1.0:** první verze

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
  DebugLog.java       – záznam pro řešení potíží
  Prefs.java, ColorUtil.java
```

## Licence

[MIT](LICENSE)
