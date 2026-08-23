# Phase 12 güvenlik incelemesi

Tarih: 22 Ağustos 2026

## Sonuç

Kod ve yapılandırma incelemesinde release'i açık geliştirme ayarlarıyla dağıtacak bir yol
bırakılmadı. Aşağıdaki dış işlemler tamamlanmadan uygulama aile kullanımına hazır sayılmaz:
gerçek Firebase yapılandırması/dağıtımı, release anahtarı yedeği ve fiziksel cihaz kabulü.

## Yerel veri ve yedekleme

- İlaç programı, oluşum ve outbox kayıtları uygulamanın özel Room veritabanındadır.
- PIN, telefon hedefi, cihaz kimliği ve ayarlar özel DataStore alanındadır.
- `allowBackup=false` yanında Android 12+ cloud backup ve device-transfer kuralları tüm dosya,
  veritabanı, shared preferences ve dış uygulama alanlarını açıkça hariç tutar.
- Yeni cihaz kurulumu otomatik tıbbi veri geri yüklemesine güvenmez.

## Ağ ve bulut sınırı

- Release yapılandırması cleartext HTTP'yi reddeder.
- Yalnız debug kaynak seti, Firebase emülatörleri için `10.0.2.2` cleartext erişimine izin verir.
- Yerel ilaç alarmı Firebase, FCM, Firestore dinleyicisi veya WorkManager tarafından
  tetiklenmez.
- FCM payload'ında ilaç adı, doz metni veya talimat yoktur; payload yalnız dar kimlik ve olay
  zaman bilgisi taşır. Firestore görünümü yetkili durum kaynağıdır.
- Firestore kuralları varsayılan olarak reddeder, aile üyeliğini/rolünü ve eşleştirilmiş cihazı
  doğrular. Aile cihazı uzaktan ilaç programı yazamaz.
- Occurrence projeksiyonu aynı atomik yazımdaki değiştirilemez sync olayı, kaynak cihazı,
  durum ve artan sürüm ile eşleşmek zorundadır.

## Android bileşen ve izin incelemesi

- Yanında'nın dışa açık tek kendi bileşeni launcher `MainActivity`'dir. Alarm activity,
  receiver'lar ve FCM service dışa kapalıdır.
- Merged manifestte WorkManager job/diagnostics, Firebase Auth callback, FCM receiver, Google
  Play revocation ve Profile Installer bileşenleri bulunur. Bunlar Android/Google SDK'larının
  tanımladığı `BIND_JOB_SERVICE`, `DUMP`, C2DM SEND veya revocation izinleriyle korunur ya da
  dar callback URI'sine sahiptir; release merged manifesti ayrıca incelenmiştir.
- Konum, kişi listesi, mikrofon, kamera, SMS ve doğrudan arama izni yoktur.
- Aileyi ara aksiyonu yalnız kullanıcıya numara gösteren sistem telefon çeviricisini açar;
  uygulama kendi başına arama başlatmaz.
- Uygulamanın doğrudan istediği izinler yerel alarm için bildirim, yeniden başlatma, titreşim,
  exact alarm/full-screen ve aile senkronizasyonu için internet ile sınırlıdır. WorkManager ve
  Firebase bağımlılıkları merged manifestte wake lock, ağ durumu, foreground service, C2DM ve
  Google services okuma izinlerini ekler.

## Log ve gizli bilgi incelemesi

- İlaç adı/dozu, telefon numarası, e-posta, auth token, FCM Installation ID, aile kimliği ve PIN
  loglanmaz.
- Hata logları genel bileşen mesajlarıdır; exception payload'ı üretim loguna eklenmez.
- Gerçek `google-services.json`, keystore ve yerel imzalama dosyaları Git dışında tutulur.
- Cloud Functions tamamlanma logu yalnız başarı/başarısızlık sayısını içerir.

## Kalan kabul koşulları

- Gerçek Firebase projesinde Auth sağlayıcıları, Firestore rules/indexes ve Functions kontrollü
  biçimde dağıtılmalı ve ayrı test aile hesabıyla doğrulanmalıdır.
- Release anahtarı ile parola için iki çevrimdışı yedek oluşturulmalıdır.
- V1'de PIN kurtarma akışı yoktur; PIN parola yöneticisinde tutulmalı ve unutulması halinde
  yerel kurulum güvenilir yazılı talimatlardan yeniden yapılmalıdır.
- `npm audit` high/critical bulmadı; güncel doğrudan Firebase paketlerinin altında üretim
  Functions ağacında 7 ve geliştirme aracı ağacında 5 adet transitive `moderate` kayıt vardır.
  Önerilen `--force` çözümü doğrudan Firebase paketlerini eski/breaking sürümlere düşürdüğü için
  uygulanmamıştır; upstream güncellemeleri izlenmelidir.
- Galaxy A06 Android 16 ve kullanılacak aile/bakıcı telefonu üzerinde
  `docs/release-test-record.md` tamamlanmalıdır.
