# Yanında release kabul kaydı

Bu kayıt Phase 12'nin fiziksel kabul kapısıdır. Emülatör ve otomatik test sonuçları Samsung
Galaxy A06 davranışının yerine geçmez.

## Release adayı

- Tarih: 24 Ağustos 2026
- Uygulama: Yanında
- Application ID: `com.berkant.yaninda`
- Version name / code: `1.0.1` / `2`
- Commit: Çalışma ağacı henüz commit edilmedi
- Release APK: **OLUŞTURULMADI**
- İmzalama sertifikası: **OLUŞTURULMADI / DOĞRULANMADI**
- Geliştirme APK'sı: `app/build/outputs/apk/debug/app-debug.apk` (yalnız emülatör ve geliştirme testi)
- Gerçek Firebase yapılandırması: **YOK / DOĞRULANMADI**

## Fiziksel cihaz bilgileri

### Birincil cihaz

- Model: Samsung Galaxy A06
- Tam model kodu:
- Android / build:
- One UI:
- Test eden / tarih:

### İkincil aile/bakıcı telefonu

- Model: **BİLDİRİLMEDİ**
- Android / build:
- Test eden / tarih:

## Otomatik doğrulamalar

| Kontrol | Sonuç | Not |
|---|---|---|
| `assembleDebug` | PASS | Güncel kaynak hali 24 Ağustos 2026'da derlendi. |
| JVM unit tests | PASS | `:app:testDebugUnitTest` geçti. |
| Android lint | PASS | `:app:lintDebug` geçti. |
| Android 16 emulator akış testi | PASS | Dede/Admin rolü, sekmeler, geri akışı, kilitli alarm, ACK senkronizasyonu ve Admin geçmişi doğrulandı. Ayrıntı `phase6-a06-test-matrix.md` içinde. |
| Firebase Auth/Firestore emulator rules | PASS | Ücretsiz manuel authorization ve yetki iptali regresyonları dahil test paketi geçti. |
| Cloud Functions testleri | PASS (EMULATOR ONLY) | Callable provisioning ve veri içermeyen bildirim payload'ları yerel geliştirme için test edildi; Blaze gerektirdiği için production'a dağıtılmayacak. |
| Signed release APK / certificate verify | NOT RUN | Release keystore ve üretim ortamı henüz hazırlanmadı. |
| Signed release Android 16 smoke test | NOT RUN | İmzalı release APK henüz yok. |
| Dependency audit | PASS WITH NOTE | High/critical yok; transitive moderate kayıtlar güvenlik incelemesinde belgeli. |

## Galaxy A06 kritik kabul matrisi

Her satıra `PASS`, `FAIL` veya `BLOCKED` yazın ve ekran görüntüsü/video adını ekleyin.

| ID | Senaryo | Beklenen | Sonuç / kanıt |
|---|---|---|---|
| R-01 | Normal, ekran açık | Yerel alarm zamanında açılır; ses/titreşim ve aksiyonlar çalışır. | ÇALIŞTIRILMADI |
| R-02 | Ekran kilitli | İzin varsa full-screen; yoksa yüksek öncelikli bildirim yedeği görünür. | ÇALIŞTIRILMADI |
| R-03 | Uygulama arka planda / recents'tan çıkarılmış | Alarm süreçten bağımsız tetiklenir. | ÇALIŞTIRILMADI |
| R-04 | Süreç öldürülmüş, zorla durdurma değil | Alarm tetiklenir ve tek occurrence işlenir. | ÇALIŞTIRILMADI |
| R-05 | Yeniden başlatma + ilk kilit açma | Gelecek occurrence'lar Room'dan yeniden kurulur. | ÇALIŞTIRILMADI |
| R-06 | Pil Tasarrufu | Alarm ve açık tanılama/fallback davranışı doğrulanır. | ÇALIŞTIRILMADI |
| R-07 | Samsung Uyuyan uygulamalar | Kısıtlama belgelenir; release kurulumu bu listede bırakılmaz. | ÇALIŞTIRILMADI |
| R-08 | Samsung Derin uyuyan uygulamalar | Kısıtlama belgelenir; uygulama listeden çıkarılır. | ÇALIŞTIRILMADI |
| R-09 | Samsung Asla uyumayan uygulamalar | Kilit/reboot testleri tekrar PASS olur. | ÇALIŞTIRILMADI |
| R-10 | Exact alarm erişimi kapalı/açık | Kapalı durum açıkça gösterilir; açılınca alarm planı yenilenir. | ÇALIŞTIRILMADI |
| R-11 | Bildirim izni kapalı/açık | Sessiz başarısızlık olmaz; izin durumu ve yönlendirme görünür. | ÇALIŞTIRILMADI |
| R-12 | Full-screen erişimi kapalı | Yüksek öncelikli bildirim fallback'i görünür. | ÇALIŞTIRILMADI |
| R-13 | Ses ve titreşim | Gerçek ortamda anlaşılır ses ve titreşim doğrulanır. | ÇALIŞTIRILMADI |
| R-14 | Erteleme ve sınır | Yalnız yapılandırılmış süre/sınır uygulanır; çift alarm oluşmaz. | ÇALIŞTIRILMADI |
| R-15 | Onay ve hızlı çift dokunma | Tek idempotent “aldığını onayladı” kaydı oluşur. | ÇALIŞTIRILMADI |
| R-16 | 30 dakika onay penceresi | Onay yoksa yalnız “Henüz onay yok” oluşur; alınmadı iddiası yoktur. | ÇALIŞTIRILMADI |
| R-17 | İnternet yok / sonra geri gelir | Alarm yerel çalışır; outbox daha sonra idempotent senkronize olur. | ÇALIŞTIRILMADI |
| R-18 | Büyük font + TalkBack | Metinler taşmadan kullanılabilir; odak/etiketler açık, hedefler en az 48 dp'dir. | ÇALIŞTIRILMADI |
| R-19 | Aynı anahtarlı APK güncellemesi | Uygulama kaldırılmadan güncellenir; Room migration ve alarmlar korunur. | ÇALIŞTIRILMADI |
| R-20 | Gerçek sabit program düzenleme | Eski gelecek alarmlar iptal, yeni occurrence'lar tekil kurulur. | ÇALIŞTIRILMADI |

## Diğer aile cihazları

`PRIMARY` / `SECONDARY` ayrımı yoktur. Dede ve Anneanne telefonları ayrı `ALARM_DEVICE`
kurulumlarıdır ve her biri aynı fiziksel alarm matrisinden bağımsız olarak geçmelidir. Berkant
ve Anne telefonları `ADMIN_DEVICE` olarak yönetim ve geçmiş akışlarını kullanır; ilaç alarmı
planlamaz.

## Karar

- Otomatik kalite kapısı: **PASS**
- Galaxy A06 fiziksel kabulü: **ÇALIŞTIRILMADI**
- Diğer aile cihazlarının fiziksel kabulü: **ÇALIŞTIRILMADI**
- Aile kullanımına release kararı: **NO — fiziksel kabul ve gerçek Firebase kurulumu tamamlanmadı**
