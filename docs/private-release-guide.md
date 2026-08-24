# Yanında özel kurulum ve güncelleme rehberi

Bu uygulama aile içinde özel dağıtım için hazırlanmıştır. APK'yı herkese açık bir dosya
alanına yüklemeyin. Gerçek ilaç bilgilerini yalnız güvendiğiniz cihazlarda kullanın.

`app-debug.apk` geliştirme anahtarıyla imzalanır ve WhatsApp gibi bir kanaldan gönderildiğinde
Samsung/Google Play Protect “cihazınızı korumak için engellendi” uyarısı verebilir. Play Protect'i
kalıcı olarak kapatmayın; aile cihazlarına yalnız doğrulanmış, aynı özel release anahtarıyla
imzalanmış `app-release.apk` gönderin.

## Release ön koşulları

1. Fiziksel Galaxy A06 kabul tablosunu tamamlayın.
2. Gerçek Firebase Spark projesini kurun; fiziksel cihazları Firebase Console'daki
   `deviceAuthorizations` belgeleriyle tek tek onaylayın ve `app/google-services.json` dosyasını
   yalnız geliştirme bilgisayarında tutun.
3. Firestore kurallarını dağıtmadan önce emülatör testlerini çalıştırın. Cloud Functions ücretsiz
   production yolunda dağıtılmaz; Blaze planı gerektirir.
4. Sürüm numarasını kontrol edin. Her güncellemede `versionCode` önceki APK'dan büyük olmalı.

Yeni bir alarm cihazının ilaç programını ilk kez alabilmesi için güvenli Firebase provisioning
ve en az bir başarılı schedule senkronizasyonu gerekir. Sonrasında son çalışan Room programı ve
AlarmManager yolu internet olmadan çalışır.

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
4. Telefon sahibini seçin: **Dede**, **Anneanne**, **Berkant** veya **Anne**. Yanlış profil
   seçildiyse onay vermeden önce kurulumu durdurun; production provisioning yetkisini Firebase
   Console'da elle oluşturulan UID + deviceId + role authorization belgesi belirler.
5. Admin telefondan yalnız açıkça test olarak adlandırılmış bir program yayınlayın ve alarm
   telefonunda sıradaki zamanın doğru göründüğünü doğrulayın.
6. Bildirim, tam zamanlı alarm, tam ekran, ses, titreşim ve Samsung arka plan ayarlarını
   kontrol edin; `docs/phase6-a06-test-matrix.md` kaydını tamamlayın.
7. Dede/Anneanne ekranında ayar veya yönetici menüsü bulunmaması normaldir. İlaç programı ve
   aile kişileri yalnız yetkili Admin telefondan yönetilir.

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
