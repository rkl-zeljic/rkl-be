# Changelog

Sve značajne promene u RKL aplikaciji prate se u ovom fajlu.
Format prati [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.2.0] - 2026-05-16
### Added
- DatePicker komponenta (srpski locale, format `dd.MM.yyyy.`) na formama otpremnice i prevoznice
- Filteri po robi, prevozniku, primaocu i pošiljaocu pri kreiranju fakture (multi-select)
- Opcija da li faktura koristi izvorne (raw) ili grupisane (label) vrednosti pošiljaoca/poručioca/primaoca/robe/prevoznika
- Otpremnica bez merenja i samostalna prevoznica (bez otpremnice) kreiraju merenje bez mernog lista
- Automatski predlog tare iz poslednjeg merenja po registraciji prilikom kreiranja otpremnice

### Changed
- `merenja.merni_list_br` više nije obavezno polje — jedinstvenost se i dalje čuva po kupcu
- Brisanje labele ili varijacije vraća povezana merenja na izvorne (raw) vrednosti umesto da ostavlja nepoznate ID-jeve

## [0.1.0] - 2026-04-26
### Added
- Verzija aplikacije se prikazuje u zaglavlju
- Stranica `Changelog` dostupna administratorima u sekciji "Administracija"
- Backend endpoint `/api/v1/version` (javan) i `/api/v1/changelog` (admin)
