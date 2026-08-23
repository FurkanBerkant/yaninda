# Phase 6 — Galaxy A06 güvenilirlik test matrisi

Bu belge Phase 6 fiziksel kabul kaydıdır. Emulator testleri geliştirme için yararlıdır ancak
Samsung Galaxy A06 davranışını doğrulamaz.

## Kabul durumu

- Fiziksel Galaxy A06 testi: **ÇALIŞTIRILMADI**
- Phase 6 fiziksel kabulü: **RELEASE ÖNCESİNE ERTELENDİ**
- Phase 7 geçişi: Repository sahibi 21 Ağustos 2026 tarihinde fiziksel kabul riskini
  açıkça erteleyerek Phase 7 geliştirmesine devam edilmesini onayladı.

Bu karar emülatör sonucunu Galaxy A06 testi yerine koymaz ve alarm güvenilirliğini fiziksel
olarak kanıtlanmış saymaz. Aşağıdaki kritik matris release kabulünden önce hâlâ tamamlanmalıdır.

## Test kaydı

- Test tarihi:
- Test eden:
- Cihazın Ayarlar ekranındaki tam model kodu:
- Android sürümü / yapı numarası:
- Yanında uygulama sürümü / commit:
- Samsung One UI sürümü:

## Güvenli test verisi

Gerçek ilaç veya doz bilgisi kullanmayın. Zamanlı program testi gerekirse şu açık test
kaydını oluşturun:

- İlaç adı: `TEST — GERÇEK İLAÇ DEĞİL`
- Doz metni: `Test kaydı`
- Talimat: `Gerçek ilaç değildir`
- Saat: Testten en az 5–10 dakika sonrası

Test bitince kaydı pasifleştirin. Uygulama hiçbir testte doz hesaplamamalı, önermemeli veya
“onay yok” durumunu ilacın alınmadığına dair kesin kanıt olarak göstermemelidir.

## Ön koşullar

1. Uygulama kurulduktan sonra en az bir kez elle açılmış olmalı. Android, uygulama ilk kez
   açılmadan `BOOT_COMPLETED` yayınını uygulamaya teslim etmeyebilir.
2. Bakıcı PIN’i ve test için aile telefonu yerel olarak ayarlanmalı.
3. Bildirim, tam zamanlı alarm ve tam ekran durumları bakıcı ekranında kontrol edilmeli.
4. Samsung’da `Ayarlar → Pil ve cihaz bakımı → Pil → Arka plan kullanım sınırları` açılmalı.
5. Yanında, **Uyuyan uygulamalar** ve **Derin uyuyan uygulamalar** listelerinde olmamalı;
   **Asla uyumayan uygulamalar** listesine eklenmeli.
6. Yeniden başlatma testinde cihaz ilk kez kilit açıldıktan sonra Room verisi erişilebilir olur
   ve alarmlar yeniden kurulur. İlk kilit açılmadan alarm verme bu sürümün kabul edilen bir
   özelliği değildir.

## Kritik test matrisi

Her satıra `PASS`, `FAIL` veya `BLOCKED` yazın; ekran görüntüsü/video ve gözlemi not edin.

| ID | Senaryo | Uygulama adımları | Beklenen sonuç | Sonuç / kanıt |
|---|---|---|---|---|
| P6-01 | Ekran açık | Bakıcı ekranından 1 dakika test alarmı kurun. | Ses, titreşim ve test alarm ekranı görünür; son test alarmı zamanı kaydedilir. | ÇALIŞTIRILMADI |
| P6-02 | Ekran kapalı ve kilitli | Test alarmını kurun, ekranı kilitleyin. | İzin varsa tam ekran açılır; yoksa yüksek öncelikli bildirim görünür. Sessiz başarısızlık olmaz. | ÇALIŞTIRILMADI |
| P6-03 | Arka plan / son uygulamalardan çıkarılmış | Testi kurun, ana ekrana dönün ve uygulamayı son uygulamalardan kaydırın. | Alarm zamanında tetiklenir. | ÇALIŞTIRILMADI |
| P6-04 | İşlem öldürülmüş | Test programı kurulu iken uygulamayı arka plana alın; geliştirme aracıyla süreci öldürün, **Zorla durdur** kullanmayın. | Kalıcı `AlarmManager` alarmı sürece bağlı olmadan tetiklenir. | ÇALIŞTIRILMADI |
| P6-05 | Yeniden başlatma | En az 10 dakika sonrasına açık test programı kurun; telefonu yeniden başlatın ve ilk kilidi açın. Bakıcı ekranında sıradaki alarmı kontrol edin. | `BOOT_COMPLETED` sonrası Room planı yeniden kurulur; alarm belirlenen zamanda tetiklenir. | ÇALIŞTIRILMADI |
| P6-06 | Pil tasarrufu | Pil Tasarrufu’nu açın ve kilitli ekran testini tekrarlayın. | Bakıcı ekranı Pil Tasarrufu’nu “Açık” gösterir; test alarmı tetiklenir. | ÇALIŞTIRILMADI |
| P6-07 | Samsung Uyuyan uygulamalar | Yanında’yı geçici olarak Uyuyan uygulamalar listesine ekleyip davranışı kaydedin; sonra listeden çıkarın. | Kısıtlama davranışı belgelenir. Bu liste release kurulumu için kabul edilmez. | ÇALIŞTIRILMADI |
| P6-08 | Samsung Derin uyuyan uygulamalar | Yanında’yı geçici olarak Derin uyuyan uygulamalara ekleyip davranışı kaydedin; sonra mutlaka çıkarın. | Güvenilmez/engellenen arka plan davranışı belgelenir. Bu liste release kurulumu için kabul edilmez. | ÇALIŞTIRILMADI |
| P6-09 | Samsung Asla uyumayan | Yanında’yı Asla uyumayan uygulamalara ekleyin ve P6-02/P6-05’i tekrarlayın. | Kilitli ekran ve yeniden başlatma testleri PASS olur. | ÇALIŞTIRILMADI |
| P6-10 | Exact alarm erişimi kapalı | Erişimi kapatın, bakıcı ekranını yeniden açın ve test düğmesini kontrol edin. | “İzin gerekli” görünür; test yanlış biçimde hazır sayılmaz. Erişim açılınca plan tekrar kurulur. | ÇALIŞTIRILMADI |
| P6-11 | Bildirim izni kapalı | Bildirim iznini kapatın ve tanılamayı yenileyin. | Durum açıkça kapalı/izin gerekli görünür; test sessizce kurulmaz. İzin açılınca test çalışır. | ÇALIŞTIRILMADI |
| P6-12 | Tam ekran erişimi kapalı | Tam ekran erişimini kapatıp kilitli ekran testi yapın. | Tanılama “İzin gerekli” gösterir ve yüksek öncelikli bildirim yedeği görünür. | ÇALIŞTIRILMADI |
| P6-13 | Son alarm kalıcılığı | Bir test alarmı çalıştırın; uygulama sürecini kapatıp yeniden açın. | Son test alarmı zamanı ve teslim sonucu bakıcı ekranında kalır. | ÇALIŞTIRILMADI |
| P6-14 | Erteleme | Açık test programı alarmında izin verilen ertelemeyi seçin. | Yeni alarm yalnız yapılandırılan süre ve sınırla kurulur; eski bildirim kapanır. | ÇALIŞTIRILMADI |
| P6-15 | Onay / çift dokunma | “İLACIMI ALDIM” ardından “EVET, ALDIM”a hızlıca iki kez dokunun. | Tek idempotent onay kaydı oluşur; yeniden alarm verilmez. | ÇALIŞTIRILMADI |
| P6-16 | Büyük yazı ve TalkBack | Sistem yazı boyutunu büyütün; TalkBack ile ana alarm ve onay akışını gezin. | Kritik metin/aksiyonlar okunur, anlam yalnız renge bağlı değildir ve hedefler en az 48 dp’dir. | ÇALIŞTIRILMADI |

## Ek gözlem matrisi

Bu koşulların sonucu cihaz/ayar bağımlıdır; gerçek davranış ve kullanılan ayar açıkça yazılmalıdır.

| ID | Koşul | Kaydedilecek gözlem | Sonuç / kanıt |
|---|---|---|---|
| O-01 | Rahatsız Etmeyin açık | Ses, titreşim, tam ekran ve bildirim davranışı | ÇALIŞTIRILMADI |
| O-02 | Telefon sessizde | Ses ve titreşim davranışı | ÇALIŞTIRILMADI |
| O-03 | Alarm sesi düşük/yüksek | Her iki seviyede anlaşılabilirlik | ÇALIŞTIRILMADI |
| O-04 | Saat dilimi / saat değişikliği | Eski alarmın iptali ve yeni yerel saate göre plan | ÇALIŞTIRILMADI |
| O-05 | Uygulama güncellemesi | Yerel kayıtların ve gelecek alarmların durumu | ÇALIŞTIRILMADI |

## Android “Zorla durdur” notu

Sistem Ayarları’ndaki **Zorla durdur**, normal süreç ölümü değildir. Android, kullanıcı uygulamayı
yeniden açana kadar receiver/alarm çalışmasını bilinçli olarak engelleyebilir. P6-04 normal süreç
ölümünü test eder. Zorla durdur davranışı ayrıca gözlem olarak kaydedilebilir ancak uygulama bunu
arka planda aşmış gibi göstermemelidir.

## Emulator smoke testi

Emülatörde P6-01, P6-02, P6-10, P6-11, P6-12, P6-13 ve yeniden başlatıp ilk kilidi açarak
P6-05 akışı denenebilir. Emülatör üreticisi Samsung olmadığı için P6-06–P6-09 sonuçları fiziksel
Galaxy A06 kabulü yerine geçmez.

### 21 Ağustos 2026 geliştirme kontrolü

Bu kayıt `Medium_Phone`, Android 16 / API 36 emülatöründe alınmıştır. Fiziksel test
tablosundaki `ÇALIŞTIRILMADI` sonuçlarını değiştirmez.

| Kontrol | Sonuç | Gözlem |
|---|---|---|
| Uygulamanın açılması | PASS | Dede ana ekranı yüklendi. |
| Bağlı Android cihaz testleri | PASS | Android 16 emülatöründe 12 testin 12’si geçti; Room 1→2 ve 2→3 migration testleri dahil. |
| Bildirim izni akışı | PASS | Tanılama önce “İzin gerekli”, sistem izni verildikten sonra “Hazır” gösterdi. |
| Exact alarm kaydı | PASS | Bir dakikalık test `RTC_WAKEUP` exact alarm olarak sisteme kaydedildi. |
| Kilitli ekranda görsel alarm | PASS | Kilitli emülatörde tam ekran “YEREL ALARM TESTİ” açıldı. |
| Ses ve titreşim | DOĞRULANMADI | Görsel emülatör kontrolü insan işitme/dokunma doğrulamasının yerine geçmez. |
| Son test alarmının kalıcılığı | PASS | Süreç kapatılıp uygulama yeniden açıldığında zaman ve teslim sonucu korundu. |
| Pil ayarları düğmesi | PASS | Samsung olmayan emülatörde Android uygulama bilgi/pil ayarları ekranı açıldı. |
| Samsung üretici kontrolü | PASS | Panel cihazı Samsung saymadı ve A06 testini ayrıca gerekli gösterdi. |
| Yeniden başlatmada program alarmını geri kurma | ÇALIŞTIRILMADI | Güvenli sahte sabit program oluşturulmadığı için P6-05 emülatörde tamamlanmadı. |
| Exact alarm kapalı durumu | ÇALIŞTIRILMADI | Yalnız hazır durum doğrulandı; P6-10’un kapalı durumu fiziksel matriste bekliyor. |
| Tam ekran erişimi kapalı yedeği | ÇALIŞTIRILMADI | Yalnız hazır durum doğrulandı; P6-12’nin kapalı durumu fiziksel matriste bekliyor. |

Debug bakım ekranını açık uygulamaya güvenilir biçimde geçirmek için:

```bash
adb shell am start --activity-single-top \
  -n com.berkant.yaninda/.MainActivity \
  --es prototype_screen caregiver
```
