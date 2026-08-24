# Yanında Design System

## Product idea

Yanında iki farklı deneyimdir:

- Alarm telefonunda sakin, tek görevli bir ev cihazı.
- Yönetici telefonunda güvenilir, açıklanabilir bir aile özeti.

Hatırlanması gereken cümle:

> Dede tek bakışta ne yapacağını anlar; aile gördüğü duruma neden güvenebileceğini bilir.

Görsel kalite hiçbir zaman ilaç güvenliği, yerel alarm güvenilirliği, çevrimdışı çalışma veya bilişsel erişilebilirliğin önüne geçmez.

## Research synthesis

Öne çıkan ilaç uygulamalarının ortak güçlü tarafları:

- Apple Health Medications: sakin yüzeyler, belirgin gün/saat ayrımı ve kısa durum dili.
- Medisafe: ilacı tanınabilir görselle destekleme ve zaman çizelgesi yaklaşımı.
- Hero / MedMinder: yaşlı kullanıcının tarafında az seçenek, belirgin fiziksel/ekran eylemi; yönetimi aile tarafına taşıma.
- Pillboxie: hızlı anlaşılır saat ve ilaç görseli, düşük kurulum sürtünmesi.

Yanında bunları daha da sadeleştirir. Alarm telefonunda istatistik, hava durumu, alt menü, gizli hareket veya yönetici kontrolü bulunmaz.

Araştırma dayanakları:

- Older-adult mobile design review: https://mhealth.jmir.org/2023/1/e43186/
- NIA cognitive-impairment communication guidance: https://www.nia.nih.gov/health/health-care-professionals-information/caring-older-patients-cognitive-impairment
- Alzheimer's Association medication safety: https://www.alz.org/help-support/caregiving/safety/medication-safety
- Android accessibility principles: https://developer.android.com/guide/topics/ui/accessibility/principles
- Android exact alarms: https://developer.android.com/develop/background-work/services/alarms

## Visual direction

Adı: **Calm appliance + trusted family console**.

- Sıcak beyaza yakın açık arka plan.
- Koyu lacivert/siyaha yakın ana metin.
- Sakin mavi: gezinme, bilgi ve güven hissi.
- Yeşil: yalnız olumlu/onaylanmış durumlar.
- Kırmızı: yalnız alarm ve gerçekten dikkat isteyen durumlar.
- Renk hiçbir zaman tek başına anlam taşımaz; ikon ve kısa metin eşlik eder.
- Kartlar arasında geniş boşluk vardır. Her metin ayrı bir karta dönüştürülmez.
- Fotoğraf/illüstrasyon yalnız bağlam ve tanınırlık sağladığında kullanılır.

### Color tokens

| Token | Light | Purpose |
| --- | --- | --- |
| Background | `#F3F7FB` | Sakin ana yüzey |
| Surface | `#FFFFFF` | Kart ve alt bar |
| Ink | `#101820` | Ana metin |
| Muted ink | `#4E5D69` | Yardımcı metin |
| Primary blue | `#096FAF` | Bilgi ve ana yönetici eylemleri |
| Success green | `#138A4B` | Onaylandı / olumlu |
| Alarm red | `#D52D3D` | İlaç alarmı / kritik dikkat |
| Warning amber | `#9A5B00` | Bekleme / eksik hazırlık |

Koyu tema desteklenir ancak alarm kırmızısı, başarı yeşili ve metin kontrastı aynı semantik anlamı korur.

## Typography

Android sistem sans-serif ailesi kullanılır. Bu bilinçli bir güvenilirlik kararıdır:

- Türkçe karakter kapsamı cihazda hazırdır.
- İnternet veya indirilebilir font gerekmez.
- Android font ölçekleme ve TalkBack davranışı öngörülebilirdir.
- Samsung Galaxy A06 üzerinde ek font yükleme maliyeti yoktur.

Alarm telefonu:

- Saat: 56–72sp, kalın.
- Ana başlık: 28–36sp, kalın.
- İlaç adı / temel durum: 22–28sp.
- Yardımcı metin: en az 18sp.

Yönetici telefonu:

- Ekran başlığı: 28sp.
- Bölüm başlığı: 20–22sp.
- Gövde: 16sp.
- Kritik durum etiketi: en az 14sp, metin + ikon.

Metin 1.3x–1.5x font ölçeğinde kırpılmamalı; sabit yükseklik yerine minimum yükseklik kullanılmalıdır.

## Layout and shape

- Temel grid: 8dp.
- Telefon yatay kenar boşluğu: 20dp; dede ana ekranında geniş cihazlarda 24dp.
- Kart iç boşluğu: 18–24dp.
- Bölümler arası: 16–24dp.
- Küçük kontrol yarıçapı: 12dp.
- Kart yarıçapı: 24–26dp.
- Ana eylem yarıçapı: 20–24dp.
- Ana eylem yüksekliği: 72dp tercih edilir.
- Hiçbir dokunma hedefi 48dp altına düşmez.

Gölgeler hafiftir. Hiyerarşi öncelikle boşluk, yüzey tonu ve tipografiyle kurulur.

## Grandfather home

Yukarıdan aşağıya:

1. Büyük tarih ve saat içeren atmosferik hero alanı. Hava durumu gösterilmez.
2. “Şu anda ilaç zamanı değil” gibi tek cümlelik durum alanı.
3. Sıradaki dozun saati ve aynı saatteki tüm ilaçlar.
4. Her zaman görünür büyük “AİLEYİ ARA” eylemi.

Aile telefonu ayarlı değilse eylem kaybolmaz. Devre dışı görünür ve yanında “Aile telefonu henüz ayarlanmamış” açıklaması bulunur.

Alarm telefonunda alt gezinme, ayarlar, geliştirici düğmesi, yoğun istatistik veya admin eylemi bulunmaz.

## Medication alarm

- Üst bölüm alarm kırmızısıdır ve “İLAÇ ZAMANI” ile saati taşır.
- Aynı saate ait tüm ilaçlar tek listede görünür.
- İlaç adı en güçlü içeriktir; doz ve talimat yalnız yöneticinin girdiği metin olarak gösterilir.
- “İLACIMI ALDIM” yeşil, tam genişlikte ve en az 72dp’dir.
- Erteleme varsa daha düşük görsel önemdedir.
- “AİLEYİ ARA” metin + telefon ikonu ile görünür.
- Ekran Galaxy A06 üzerinde normal font ölçeğinde kaydırma gerektirmemeyi hedefler; büyük fontta güvenli kaydırma kabul edilir.

Mevcut safety dokümanları onay ekranını zorunlu tuttuğu için “İlacını aldın mı?” adımı korunur. Bu davranış ancak safety kararı açıkça güncellenirse kaldırılır.

## Admin console

Alt gezinme:

1. Ana Sayfa
2. İlaçlar
3. Geçmiş
4. Ayarlar

Ana sayfa:

- Cihazın gerçek son başarılı senkronizasyon zamanına göre bağlantı durumu.
- Bugünün planlanan dozları.
- Durum her zaman metin + ikon/etiket ile gösterilir.
- Gelecekteki aynı saatli kayıt bugünün durumunu gölgeleyemez.

Geçmiş:

- Gün başlıklarına ayrılır.
- Her planlanan doz ayrı satırdır; aynı saatli ilaçlar tek logical dose group olarak gösterilebilir.
- Onay varsa “Aldığını onayladı” ve onay saati görünür.
- Onay yoksa kesin “almadı” denmez; “Henüz onay yok” kullanılır.
- Çoklu alarm cihazı raporları logical `occurrenceId` üzerinden birleştirilir.

Ayarlar yalnız şu ana grupları gösterir:

- Aile Kişileri
- Cihazlar
- Bildirimler

Legacy hesap, pairing/invitation ve “Uygulama Kontrolü” son kullanıcı akışından çıkarılır.

## Motion

- Dekoratif sürekli animasyon yoktur.
- Durum değişimi için kısa 150–250ms fade/size geçişi kullanılabilir.
- Alarm eylemi animasyona bağlı değildir.
- Sistem “animasyonları azalt” tercihine saygı gösterilir.

## Accessibility checklist

- Kritik eylemler 64dp+, diğer dokunma hedefleri 48dp+.
- Kritik ikonlar tek başına kullanılmaz.
- Dekoratif görseller TalkBack ağacından çıkarılır.
- Birleşik kartlar tek, anlaşılır semantics açıklamasına sahiptir.
- Başlıklar heading semantics kullanır.
- Durum yalnız renkle aktarılmaz.
- Metin büyüdüğünde yatay yan yana içerik gerektiğinde dikey akışa döner.
- Türkçe ifadeler kısa, doğrudan, suçlayıcı ve çocuklaştırıcı olmayan dildedir.
