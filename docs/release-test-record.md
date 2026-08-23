# Yanında release kabul kaydı

Bu kayıt Phase 12'nin fiziksel kabul kapısıdır. Emülatör ve otomatik test sonuçları Samsung
Galaxy A06 davranışının yerine geçmez.

## Release adayı

- Tarih: 22 Ağustos 2026
- Uygulama: Yanında
- Application ID: `com.berkant.yaninda`
- Version name / code: `1.0` / `1`
- Commit: Çalışma ağacı henüz commit edilmedi
- APK yolu: `app/build/outputs/apk/release/app-release.apk`
- APK boyutu: yaklaşık 13 MB
- APK SHA-256: `2cb71064da45ec86bf9622571a48b5743f04ba521142418697e561a74f48a99f`
- İmza sertifikası SHA-256: `c0dc8b946669fc8757abe0429e98af83568b68373b2d048fe03a4761fd49e434`
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
| `assembleDebug` | PASS | Final kaynak hali derlendi. |
| JVM unit tests | PASS | 66/66 test geçti. |
| Android lint | PASS | 0 error; 20 bilinen uyarı. |
| Android 16 emulator instrumentation | PASS | 19/19; Room migration, alarm identity ve Compose erişilebilirlik testleri dahil. |
| Firebase Auth/Firestore emulator rules | PASS | 12/12 test geçti. |
| Cloud Functions payload tests | PASS | 3/3 test geçti; payload'da ilaç/doz verisi yok. |
| Signed release APK / certificate verify | PASS | RSA 4096; APK Signature Scheme v2 doğrulandı. |
| Signed release Android 16 smoke test | PASS | Temiz kuruldu, açıldı; paket `DEBUGGABLE` değil. |
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

## İkincil aile telefonu kabul matrisi

| ID | Senaryo | Beklenen | Sonuç / kanıt |
|---|---|---|---|
| S-01 | Özellik varsayılanı | İkincil “Dedenin ilaç zamanı” hatırlatması varsayılan kapalıdır. | ÇALIŞTIRILMADI |
| S-02 | Açık + çevrimdışı cache | Cache'deki gelecek hatırlatma yerel tetiklenir; dede alarmı olduğu açıkça yazılır. | ÇALIŞTIRILMADI |
| S-03 | Reboot | Opt-in cache yeniden planlanır. | ÇALIŞTIRILMADI |
| S-04 | Tekrarlı intent | Aynı ikincil hatırlatma yalnız bir kez gösterilir. | ÇALIŞTIRILMADI |
| S-05 | Oturum kapatma | Cache, yerel alarm ve FCM kaydı temizlenir; hatırlatma kapanır. | ÇALIŞTIRILMADI |
| S-06 | FCM gecikmesi / çevrimdışı | Push yalnız ipucu olur; ekrandaki zaman damgalı Firestore durumu kaynak kalır. | ÇALIŞTIRILMADI |

## Karar

- Otomatik kalite kapısı: **PASS**
- Galaxy A06 fiziksel kabulü: **ÇALIŞTIRILMADI**
- Aile telefonu fiziksel kabulü: **ÇALIŞTIRILMADI**
- Aile kullanımına release kararı: **NO — fiziksel kabul ve gerçek Firebase kurulumu tamamlanmadı**
