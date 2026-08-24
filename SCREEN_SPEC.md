# SCREEN_SPEC.md

Bu belge Yanında V2 ekranlarının aktif şartnamesidir. Görsel kararlar `DESIGN.md`, güvenlik
sınırları `PROJECT_CONTEXT.md` ve teknik sorumluluklar `ARCHITECTURE.md` ile birlikte okunur.

## Rol modeli

İlk kurulumda dört sabit profil vardır; bunlar iki teknik role eşlenir:

- Dede telefonu → `ALARM_DEVICE`
- Anneanne telefonu → `ALARM_DEVICE`
- Berkant telefonu → `ADMIN_DEVICE`
- Anne telefonu → `ADMIN_DEVICE`

`PRIMARY` / `SECONDARY` ayrımı, e-posta/parola, davet veya pairing kodu ve caregiver PIN akışı
yoktur. Her alarm telefonu kendi lokal programından ve alarmlarından bağımsız sorumludur.

---

# A. ALARM DEVICE — DEDE / ANNEANNE

Alarm telefonu günlük kullanımda basit bir saat/alarm cihazı gibi davranır. Alt menü, ayarlar,
istatistik, yönetici eylemi, gizli hareket ve geliştirici düğmesi gösterilmez.

## G1 — Ana ekran

Amaç: Kullanıcıya tek bakışta güven vermek ve yalnız sıradaki gerekli bilgiyi göstermek.

Yukarıdan aşağıya:

1. Çok büyük tarih ve saat içeren sakin görsel alan.
2. Tek cümlelik mevcut durum: `Şu anda ilaç zamanı değil.`
3. Sıradaki logical dose group:
   - `Bugün` veya `Yarın`
   - büyük saat
   - aynı saatteki tüm ilaç adları
4. Büyük `AİLEYİ ARA` düğmesi.

Kurulu program yoksa açıkça `Kurulu ilaç programı yok` yazılır. Aile kişisi yoksa arama düğmesi
kaybolmaz; devre dışı görünür ve `Aile telefonu henüz ayarlanmamış` açıklaması gösterilir.
Hava durumu veya internetten gelen dekoratif bilgi gösterilmez.

## G2 — İlaç alarmı

Platform izin verdiğinde kilit ekranı üzerinde açılır; aksi durumda yüksek öncelikli alarm
bildirimi yedektir.

Yukarıdan aşağıya:

1. Güçlü kırmızı üst alan: `İLAÇ ZAMANI` ve büyük saat.
2. Aynı logical dose group içindeki tüm ilaçlar:
   - ilaç adı
   - yöneticinin aynen girdiği doz metni
   - yöneticinin aynen girdiği kısa talimat
3. Tam genişlikte, yeşil ve en az 72dp `İLACIMI ALDIM`.
4. Yalnız programda güvenli biçimde açıksa daha düşük önemde erteleme eylemi.
5. Metin + telefon ikonu ile `AİLEYİ ARA`.

Aynı saatte iki ilaç varsa iki alarm değil, tek ekran ve tek onay vardır. Normal font ölçeğinde
Galaxy A06 üzerinde kaydırma gerektirmemesi hedeflenir; büyük fontta içerik kesmek yerine güvenli
kaydırma kabul edilir.

## G3 — Alındı onayı

Başlık:

`İlacını aldın mı?`

Birbirinden belirgin iki büyük eylem:

- `EVET, ALDIM`
- `HAYIR`

Android Geri veya `HAYIR`, alarm ekranına döner ve alarm dikkat servisini susturmaz. `EVET,
ALDIM` lokal Room occurrence'ını idempotent biçimde günceller, outbox kaydını oluşturur, dikkat
servisini durdurur ve kısa başarı geri bildiriminin ardından gerçek ana ekrana döner. Onay
Firestore'u beklemez.

---

# B. ADMIN DEVICE — BERKANT / ANNE

Admin deneyimi güvenilir bir aile konsoludur. İlaç alarmı planlamaz veya çalmaz; programı
yayınlar ve alarm telefonlarından senkronize edilen zaman damgalı durumu gösterir.

Alt gezinme her ana ekranda sabittir:

1. `Ana Sayfa`
2. `İlaçlar`
3. `Geçmiş`
4. `Ayarlar`

## A1 — Ana Sayfa

- Başlık: `Dede Takip`
- Alarm telefonu sayısı ve gerçek freshness hesabına dayalı son bağlantı durumu.
- `Bugünün ilaçları` listesi.
- Her dozda saat, ilaç adı, yöneticinin girdiği doz metni ve metin + ikon durum etiketi.
- Onay varsa `Aldığını onayladı` ve gerçek onay saati.
- Onay yoksa kesin `almadı` denmez; `Henüz onay yok` kullanılır.
- Eski veri canlıymış gibi gösterilmez; zaman damgası ve çevrimdışı/bilinmiyor dili kullanılır.

## A2 — İlaçlar

Liste yalnız aktif sabit programları gösterir. Her kartta ad, yazılı doz/talimat, saatler ve
günler bulunur. Ekleme, düzenleme ve silme açık metinli eylemlerdir.

Formun başında kalıcı güvenlik uyarısı bulunur:

- Yalnız doktor veya eczacının sabit saat ve sabit talimat verdiği ilaçlar eklenir.
- İlaç adı, doz metni ve talimat aynen aktarılır; uygulama yorumlamaz veya hesaplamaz.
- Gerektiğinde kullanılan, ölçüme göre değişen ilaçlar ve insülin V1 kapsamında değildir.

Saat Android saat seçicisiyle girilir. Kaydetme 64dp+ belirgin bir eylemdir. Geri veya Vazgeç,
kaydetmeden listeye döner. Silme onay diyaloğu geçmiş kayıtları koruduğunu açıkça söyler.

## A3 — Geçmiş

- Gün başlıklarına ayrılır; bugün açıkça belirtilir.
- Her planlanan günlük doz ayrı satırdır.
- Aynı saatli ilaçlar tek logical dose group olarak gösterilir.
- Çoklu alarm cihazı raporları cihazdan bağımsız `occurrenceId` üzerinden birleştirilir.
- Onay varsa `Aldığını onayladı` ve onay saati görünür.
- Onay gelmediyse `Henüz onay yok` yazılır.
- Gelecek günlerin plan kayıtları geçmişin önüne geçmez.

## A4 — Ayarlar

Yalnız üç ana grup vardır:

- `Aile Kişileri`: `AİLEYİ ARA` için ad ve telefon; sistem çeviricisini açar, otomatik aramaz.
- `Cihazlar`: provision edilmiş cihazlar ve freshness temelli son görülme bilgisi.
- `Bildirimler`: bildirim izni ve ilgili sistem yönlendirmeleri.

Hesap, e-posta/parola, aile bağlantısı, invitation/pairing, caregiver PIN ve `Uygulama
Kontrolü` ekranları yoktur.

---

# C. İLK KURULUM

## S1 — Bu telefon kimin?

Dört büyük ve açık profil kartı gösterilir:

- `Dede telefonu`
- `Anneanne telefonu`
- `Berkant telefonu`
- `Anne telefonu`

Hesap, parola veya eşleştirme kodu istenmez. Seçimden sonra cihaz anonim Firebase UID, lokal
`deviceId` ve sunucu kontrollü provisioning ile sabit `sefer-family` ailesine bağlanır. Yetkisiz
production cihazı, istemcinin gönderdiği role güvenilmeden backend tarafından reddedilir.

Yanlış profil seçilirse kullanıcı gerçek ilaç programı oluşturmadan kurulumu durdurur. Günlük
Dede/Anneanne arayüzünde rol değiştirme veya admin'e geçiş yolu gösterilmez.

---

# D. GEZİNME VE GERİ DAVRANIŞI

- Alarm cihazı ana ekranında sistem Geri uygulamayı normal Android davranışıyla arka plana alabilir;
  burada kaybedilecek bir iç akış yoktur.
- Admin ana sekmelerinin herhangi birinde Geri önce `Ana Sayfa`ya döner.
- Ayarlar alt sayfasında Geri önce Ayarlar listesine, sonra Ana Sayfa'ya döner.
- İlaç formunda Geri değişiklik yazmadan İlaçlar listesine döner.
- Alarm onayında Geri alarm ekranına döner ve sesi yanlışlıkla susturmaz.
- Kritik bir akışta yalnız gizli hareket veya yalnız ikon kullanılmaz.

---

# E. GÖRSEL VE ERİŞİLEBİLİRLİK

- Sakin açık arka plan, koyu metin, mavi bilgi, yeşil onay, kırmızı alarm.
- Durum anlamı hiçbir zaman yalnız renkle verilmez; kısa metin ve ikon eşlik eder.
- Dede ekranında çok büyük tipografi, geniş boşluk ve az seçenek.
- Kritik eylemler tercihen 64–72dp; hiçbir dokunma hedefi 48dp altına düşmez.
- Kritik eylemlerde yalnız ikon kullanılmaz.
- Başlıklarda heading semantics, birleşik kontrollerde anlaşılır TalkBack semantics bulunur.
- Dekoratif görseller erişilebilirlik ağacından çıkarılır.
- Sabit yükseklik yerine minimum yükseklik ve gerektiğinde yeniden akış/kaydırma kullanılır.
- Türkçe dil kısa, doğrudan, suçlayıcı veya çocuklaştırıcı olmayan biçimdedir.

Alarm cihazı bir medikal dashboard gibi değil, güven veren basit bir ev cihazı gibi; Admin
telefonu ise sakin ve açıklanabilir bir aile konsolu gibi görünmelidir.

---

# F. BU SÜRÜMDE YOK

- konum / GPS / güvenli alan
- glucose takibi
- insülin, ölçüme bağlı, değişken doz veya PRN planlama
- uzaktan ilaç alma kararı veya uzaktan doz verme
- otomatik telefon araması
- tedavi/dosage önerisi veya catch-up/double-dose mantığı
- caregiver PIN, hesap ekranı, davet veya pairing kodu
