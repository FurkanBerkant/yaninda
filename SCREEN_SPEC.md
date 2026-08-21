# SCREEN_SPEC.md

## UX philosophy

There are three experiences:

1. Grandfather — almost no navigation.
2. On-site caregiver / grandmother — simple status + help.
3. Family caregiver/admin — configuration, monitoring, diagnostics.

Do not expose admin complexity to grandfather.

---

# A. GRANDFATHER SCREENS

## G1 — Home

Primary purpose:
reassure and show only the next relevant action.

Layout:

[large]
21 Ağustos Cuma
18:10

[status card]
Şu anda ilaç zamanı değil.

Sıradaki ilaç
20:00

[very large optional button]
AİLEYİ ARA

No menu required for normal daily use.

Long-press / protected entry may open caregiver access, but do not make this discoverable accidentally.

## G2 — Medication alarm

Full-screen, lock-screen capable when platform allows.

[very large]
İLAÇ ZAMANI

20:00

[large medication photo]

Şeker İlacı
1 tablet
Yemekten sonra

[PRIMARY HUGE BUTTON]
İLACIMI ALDIM

[SECONDARY]
10 DAKİKA SONRA HATIRLAT

[HELP]
AİLEYİ ARA

The screen should not require scrolling on Galaxy A06 at normal/large font where possible.

## G3 — Taken confirmation

İlacını aldın mı?

[EVET, ALDIM]

[HAYIR]

After yes:

Tamam.
Kaydedildi.

Return automatically to simple home.

## G4 — Local safety help (future location module)

Only after a configured safety-zone event.

Evden uzaklaştın.

[ANNEANNEYİ ARA]

Optional:
[EVE DÖNMEK İÇİN YARDIM]

Do not add a complex map for grandfather in first location version.

---

# B. GRANDMOTHER / ON-SITE CAREGIVER

## C1 — Today

Bugün

08:00
Aldığını onayladı ✓

13:00
Aldığını onayladı ✓

20:00
Sıradaki hatırlatma

[TEST / HELP entry hidden behind caregiver UI]

If grandmother uses a secondary reminder:
"Dedenin ilaç zamanı"

This must be clearly separate from grandfather's authoritative acknowledgement.

## C2 — Help / contact

Large buttons:
- Dede'yi ara
- [configured family member] ara
- Son durumu göster

No medication editing unless this phone is explicitly promoted to an admin role in a future version.

---

# C. PRIMARY-DEVICE CAREGIVER ADMIN

## A1 — PIN

Bakıcı Ayarları

PIN

[DEVAM]

Simple.
No grandfather-facing explanation.

## A2 — Medication list

İlaç Programı

Active fixed schedules.

Each row:
- photo
- name
- prescribed text
- times
- active/inactive

[İLAÇ EKLE]

Persistent warning:
"Bu sürüm yalnız sabit saat / sabit doz ilaçları destekler."

## A3 — Add medication: safety gate

Before form:

Bu ilaç sabit saatte ve sabit dozda mı?

[EVET]
[HAYIR / EMİN DEĞİLİM]

If NO / unsure:

"Bu ilaç için uygulamada otomatik doz hatırlatması oluşturmayın.
Doktor/eczacı talimatını doğrulayın."

Do not continue into normal dose schedule form.

This is especially important for insulin / glucose-dependent / PRN medication.

## A4 — Fixed medication form

Fields:
- Ekranda görünen ilaç adı
- Doz metni
- Kısa talimat
- Saat
- Günler
- optional photo
- snooze enabled
- snooze duration

No numeric dose calculation.
Text is caregiver-entered.

Before save:

"Bu bilgileri doktor/eczacı talimatına göre kontrol ettim."

[PROGRAMI KAYDET]

## A5 — Today/history

Timeline:

08:00 — Aldığını onayladı — 08:04
13:00 — Henüz onay yok
20:00 — Planlandı

Use acknowledgement semantics, not certainty.

## A6 — Diagnostics

Sections:

YEREL ALARM
- Bildirim
- Exact alarm
- Full-screen
- Ses
- Titreşim
- Next alarm
- Last alarm

SAMSUNG
- Sleeping apps
- Deep sleeping apps
- Never sleeping guidance

SENKRONİZASYON
- Online/offline
- Last cloud sync
- Pending outbox
- Authentication
- FCM

KONUM (if enabled later)
- precise location
- background location
- location services
- last fix
- accuracy

Actions:
- 1 dakika sonra test alarmı
- ses testi
- senkronizasyon testi
- location test

---

# D. REMOTE FAMILY SCREENS

## F1 — Family dashboard

Dede

DEVICE STATUS
Online / Offline
Son bağlantı: 18:02

BUGÜNÜN İLAÇLARI
08:00 — Aldığını onayladı
13:00 — Aldığını onayladı
20:00 — Planlandı

If stale:
"Dede telefonu çevrimdışı.
Son durum 17:34'te alındı."

Never silently show cached state as live.

## F2 — Medication occurrence detail

20:00 Hatırlatması

Planlanan: 20:00
Alarm oluştu: 20:00
Onay: 20:06
Son senkronizasyon: 20:07

Status:
"Aldığını onayladı"

Small explanatory copy:
"Bu kayıt, telefonda 'İlacımı aldım' onayının verildiğini gösterir."

## F3 — Family notification center

Examples:
- 20:06 — 20:00 ilacı için "aldım" onayı geldi.
- 20:30 — 20:00 ilacı için henüz onay yok.
- 21:10 — Dede telefonu çevrimdışı.

Avoid notification spam.
Thresholds are caregiver-configured later.

## F4 — Location safety (future)

Dede — Güvenlik

Status:
ÇEVRİMDIŞI

Son konum
18:42
Accuracy: ±18 m

[MAP]

"Bu canlı konum değildir.
Son güncelleme 28 dakika önce."

Safety zone:
Ev alanı — Son bilinen: dışında

Battery:
%37 (18:42 verisi)

If live/online:
"Konum 1 dk önce güncellendi"

Do not show a stale map without timestamp.

## F5 — Device/family management

Admin only:
- family members
- device names
- grandfather phone = PRIMARY
- grandmother phone = CAREGIVER
- revoke access
- pair new caregiver device

No medication schedule editing in remote v1.

---

# E. FIRST SETUP

## S1 — What is this phone?

Bu telefon kimin?

[DEDEMİN TELEFONU]
[AİLE / BAKICI TELEFONU]

Use caregiver-guided setup.

## S2 — Primary setup

Checklist:
- notifications
- exact alarm
- full-screen capability
- Samsung battery settings
- alarm sound test
- caregiver contact
- caregiver PIN
- initial fixed medication schedule
- optional family pairing

Location permission is NOT requested here unless location safety is explicitly enabled.

## S3 — Family pairing

Authenticated caregiver.
Pair to family.
Display:
- family name
- primary device
- role

No grandfather login flow.

---

# Visual direction

- very high contrast
- neutral warm background or white
- dark text
- restrained use of status colors
- large type
- huge rounded buttons
- no decorative complexity
- medication image should be a real caregiver-selected package/photo when used
- use text + icon together
- avoid relying on green/red only
- avoid medical-dashboard aesthetics for grandfather screens

Grandfather screens should feel closer to a simple clock/alarm than a hospital application.
