# Yanında özel kurulum ve güncelleme rehberi

Bu uygulama aile içinde özel dağıtım için hazırlanmıştır. APK'yı herkese açık bir dosya
alanına yüklemeyin. Gerçek ilaç bilgilerini yalnız güvendiğiniz cihazlarda kullanın.

## Release ön koşulları

1. Fiziksel Galaxy A06 kabul tablosunu tamamlayın.
2. Bağlantılı aile özellikleri kullanılacaksa gerçek Firebase projesini kurun ve
   `app/google-services.json` dosyasını yalnız geliştirme bilgisayarında tutun.
3. Firestore kuralları ve Cloud Functions dağıtımından önce emülatör testlerini çalıştırın.
4. Sürüm numarasını kontrol edin. Her güncellemede `versionCode` önceki APK'dan büyük olmalı.

Gerçek `google-services.json` yoksa dede telefonundaki yerel Room + AlarmManager yolu çalışır;
aile hesabı, uzaktan durum ve FCM özellikleri kullanılamaz.

## İmzalama anahtarı

Bu repository release bilgilerini dört ortam değişkeninden alır:

- `YANINDA_RELEASE_STORE_FILE`
- `YANINDA_RELEASE_STORE_PASSWORD`
- `YANINDA_RELEASE_KEY_ALIAS`
- `YANINDA_RELEASE_KEY_PASSWORD`

Anahtar dosyasını repository dışında tutun. Parolayı komut satırı geçmişine, kaynak koda,
`local.properties` dosyasına veya ekran görüntüsüne yazmayın. Bu çalışma için önerilen yer:

`/Users/berkant/.config/yaninda-signing/yaninda-release.jks`

Aynı `applicationId` ile kurulan her güncelleme aynı anahtarla imzalanmalıdır. Anahtar
kaybolursa mevcut kurulumu veri kaybetmeden güncellemek mümkün olmaz. Anahtar dosyasının en
az iki şifreli çevrimdışı yedeğini ve parolanın parola yöneticisi kaydını oluşturun. Yedekleri
aynı fiziksel yerde saklamayın.

## Release APK üretme

Parolaları etkileşimli ve görünmeden okuyup bu Terminal oturumu için dışa aktarın:

```bash
export YANINDA_RELEASE_STORE_FILE="/Users/berkant/.config/yaninda-signing/yaninda-release.jks"
export YANINDA_RELEASE_KEY_ALIAS="yaninda-release"
read -s "YANINDA_RELEASE_STORE_PASSWORD?Keystore parolası: "
echo
export YANINDA_RELEASE_STORE_PASSWORD
read -s "YANINDA_RELEASE_KEY_PASSWORD?Anahtar parolası: "
echo
export YANINDA_RELEASE_KEY_PASSWORD
./gradlew assembleRelease
```

Çıktı normalde `app/build/outputs/apk/release/app-release.apk` konumundadır. APK'yı aileye
vermeden önce Android SDK içindeki `apksigner verify --verbose --print-certs` ile doğrulayın ve
SHA-256 özetini ayrı bir kanaldan paylaşın.

## İlk özel kurulum

1. APK'yı USB kablosu veya yalnız aileye açık güvenli bir dosya aktarımıyla telefona taşıyın.
2. Samsung Ayarlar'da APK'yı açan uygulama için **Bilinmeyen uygulamaları yükle** iznini geçici
   olarak verin. One UI sürümüne göre menü adı değişebilir.
3. APK'yı kurun ve Yanında'yı en az bir kez elle açın.
4. **Dede telefonu** rolünü seçin ve bakıcı eşliğinde ilk kurulumu tamamlayın.
5. Bildirim, tam zamanlı alarm, tam ekran, ses, titreşim ve Samsung arka plan ayarlarını
   tanılama ekranından kontrol edin.
6. Uygulama simgesine uzun basıp **Bakıcı** kısayolunu seçerek PIN korumalı bakıcı ayarlarına
   daha sonra yeniden girebilirsiniz. Bu kısayol dede ana ekranında görünmez.

Bu sürümde PIN kurtarma akışı yoktur. PIN unutulursa yerel kurulumun yeniden yapılması gerekir;
bu yüzden PIN'i parola yöneticisinde saklayın ve yazılı ilaç talimatlarını uygulama dışında da
koruyun.

## Güncelleme

1. Yerel verinin ve alarm davranışının fiziksel test kaydını tamamlayın.
2. `versionCode` değerini artırın ve APK'yı aynı release anahtarıyla imzalayın.
3. Sertifika SHA-256 özetinin önceki release ile aynı olduğunu doğrulayın.
4. Yeni APK'yı mevcut uygulamanın üzerine kurun. **Önce uygulamayı kaldırmayın**; kaldırma,
   telefondaki yerel programı ve geçmişi siler.
5. Güncellemeden sonra uygulamayı açın; sıradaki alarmı, bildirim kanalını ve yeniden başlatma
   kurtarmasını tekrar test edin.

Android yedekleme bilinçli olarak kapalıdır. Yeni telefona otomatik ilaç verisi taşıma yoktur;
yeni cihaz bakıcı tarafından yeniden ve yazılı doktor/eczacı talimatına göre kurulmalıdır.
