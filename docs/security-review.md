# Phase 12 güvenlik incelemesi

Tarih: 24 Ağustos 2026

## Sonuç

Kod ve yapılandırma incelemesinde release'i açık geliştirme ayarlarıyla dağıtacak bir yol
bırakılmadı. Aşağıdaki dış işlemler tamamlanmadan uygulama aile kullanımına hazır sayılmaz:
production UID allow-list/App Check kararı, gerçek Firebase dağıtımı, release anahtarı yedeği
ve fiziksel cihaz kabulü.

## Yerel veri ve yedekleme

- İlaç programı, oluşum ve outbox kayıtları uygulamanın özel Room veritabanındadır.
- Telefon hedefi, cihaz profili/kimliği ve yerel ayarlar özel DataStore alanındadır.
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
- Firestore kuralları varsayılan olarak reddeder, aile üyeliğini/rolünü ve provision edilmiş
  cihazı doğrular. Yalnız Admin cihazı istenen programı yayınlayabilir; Alarm cihazı programı
  okuyabilir ancak değiştiremez ve yalnız kendi occurrence/device projeksiyonunu yazabilir.
- Occurrence projeksiyonu aynı atomik yazımdaki değiştirilemez sync olayı, kaynak cihazı,
  durum ve artan sürüm ile eşleşmek zorundadır.
- Provisioning backend'i `deviceId` değerini güvenli alfanümerik/alt çizgi/tire karakterleriyle
  sınırlar. Böylece istemci girdisi Firestore'daki cihaz öneki eşleştirmesini genişletemez.
- Alarm-device okuma yetkisi yalnız server-side access kaydına değil, aynı UID/role ile eşleşen
  canlı cihaz kaydına da bağlıdır. Admin cihaz kaydını sildiğinde eski access belgesi tek başına
  schedule/contact erişimini sürdüremez.

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

- İlaç adı/dozu, telefon numarası, auth token, FCM Installation ID ve aile/cihaz kimliği
  loglanmaz.
- Hata logları genel bileşen mesajlarıdır; exception payload'ı üretim loguna eklenmez.
- Gerçek `google-services.json`, keystore ve yerel imzalama dosyaları Git dışında tutulur.
- Cloud Functions tamamlanma logu yalnız başarı/başarısızlık sayısını içerir.

## Repository gizliliği ve geçmiş taraması

- GitHub repository görünürlüğü aile/sağlık bağlamı nedeniyle `PRIVATE` olarak ayarlanmıştır.
- Tüm ulaşılabilir Git commitleri yüksek güvenli özel anahtar, servis hesabı, GitHub/AWS/
  OpenAI/Stripe/Slack tokenı, release keystore'u ve takip edilen `.env` dosyası kalıpları için
  taranmıştır; aktif credential bulgusu yoktur. GitHub secret scanning açık ve açık uyarı
  sayısı sıfırdır.
- `functions/.env.local`, `app/google-services.json`, `local.properties`, emulator verisi,
  servis hesabı JSON'ları ve release imzalama materyali Git dışında tutulur.
- Yanlışlıkla izlenen Android Studio cihaz seçimi, Kotlin hata logları, UI hiyerarşi dump'ı
  ve `.single-family-backup` kopyası repository'den çıkarılmıştır. Yerel kopyalar silinmemiştir.
- Yeni commitlerde kişisel e-posta yerine GitHub `noreply` adresi kullanılmaktadır. Eski commit
  metadata'sı repository özel olduğu için yalnız yetkili kullanıcılara görünür; geçmiş yeniden
  yazılmamıştır.
- Dependabot güvenlik uyarıları ve otomatik güvenlik düzeltmeleri açılmış, Gradle ile iki NPM
  manifesti için haftalık bağımlılık kontrolü eklenmiştir.

## Kalan kabul koşulları

- Gerçek Firebase projesinde anonymous Auth, Firestore rules/indexes ve Functions kontrollü
  biçimde dağıtılmalı; Admin/Alarm UID allow-list'leri sunucu ortamında tanımlanmalı ve yanlış
  role isteyen yetkisiz bir cihazla reddetme testi yapılmalıdır.
- App Check enforcement kararı üretimde açıkça verilmeli ve etkinleştirilecekse callable
  provisioning ile Android release sertifikası üzerinden doğrulanmalıdır.
- Release anahtarı ile parola için iki çevrimdışı yedek oluşturulmalıdır.
- `npm audit` high/critical bulmadı; güncel doğrudan Firebase paketlerinin altında üretim
  Functions ağacında 7 ve geliştirme aracı ağacında 5 adet transitive `moderate` kayıt vardır.
  Önerilen `--force` çözümü doğrudan Firebase paketlerini eski/breaking sürümlere düşürdüğü için
  uygulanmamıştır; upstream güncellemeleri izlenmelidir.
- Galaxy A06 Android 16 ve kullanılacak aile/bakıcı telefonu üzerinde
  `docs/release-test-record.md` tamamlanmalıdır.
