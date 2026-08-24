# Firebase aile bağlantısı kurulumu

Bu belge yalnızca aile senkronizasyonunu kurar. Dede telefonundaki ilaç alarmı Firebase, FCM veya internet üzerinden tetiklenmez; alarmın kaynağı yerel Room + AlarmManager yoludur.

## Yerel geliştirme

Debug uygulaması gerçek buluta yazmadan `yaninda-18369` kimliğiyle yerel Firebase Auth,
Firestore ve Functions emülatörlerine bağlanır. Android emülatörü bilgisayara `10.0.2.2`
üzerinden erişir. Güvenlik kuralı testleri ise izole `demo-yaninda` proje kimliğini kullanır.

1. Bilgisayarda desteklenen bir Node.js LTS sürümü (20 veya 22) ve Java 21 kurulu olmalı.
2. Repository root'unda `npm install` çalıştırın.
3. Auth ve Firestore testlerini `npm run test:firebase` ile çalıştırın.
4. Uygulamayı emülatörle elle denemek için ayrı Terminal penceresinde şunu açık bırakın:

   ```bash
   npm run emulators:start
   ```

   Bu komut Auth ve Firestore verisini `firebase-emulator-data/` altında yerel olarak saklar,
   sonraki açılışta geri yükler. Klasör aile verisi içerebileceği için `.gitignore` kapsamındadır.

5. Android Studio'da `app` yapılandırmasını çalıştırın. İlk ekranda cihaz rolünü seçin.

Yerel emülatör kapalıysa aile işlemleri ağ hatası gösterir; yerel ilaç alarmı çalışmaya devam eder.

## Gerçek Firebase projesi — ücretsiz Spark kurulumu

Bu proje için varsayılan production yolu Spark planıdır. Kredi kartı veya Cloud Billing hesabı
bağlanmaz. Cloud Functions deploy edilmez; Cloud Functions Firebase'de Blaze planı gerektirir.
Tek Firestore veritabanı, Anonymous Authentication ve istemci senkronizasyonu ücretsiz kotalar
içinde kullanılır.

1. Firebase Console'da özel bir proje oluşturun.
2. Authentication bölümünde yalnız Anonymous sağlayıcısını etkinleştirin. V2 kullanıcı
   arayüzünde e-posta/parola veya pairing code akışı yoktur.
3. Firestore'u production mode ile oluşturun. Açık geliştirme kuralı kullanmayın.
4. Android uygulaması ekleyin; package adı tam olarak `com.berkant.yaninda` olmalı.
5. İndirilen gerçek `google-services.json` dosyasını `app/google-services.json` konumuna koyun. Bu dosya `.gitignore` içindedir; repository'ye eklemeyin.
6. `firebase use` ile doğru projeyi seçtikten sonra önce emülatör testlerini çalıştırın, sonra yalnız Firestore kuralları ve indekslerini kontrollü biçimde dağıtın:

   ```bash
   npm run test:firebase
   npx firebase deploy --only firestore:rules,firestore:indexes
   ```

7. İmzalı release APK telefonda açılıp profil seçildiğinde uygulama
   `deviceApprovalRequests/{uid}` belgesini oluşturur ve “Cihaz onay isteği gönderildi” gösterir.
8. Firebase Console > Firestore > `deviceApprovalRequests` altında ilgili belgeyi açın. Belgedeki
   `uid`, `familyId`, `deviceId` ve `requestedRole` değerlerini aynen kullanarak
   `deviceAuthorizations/{uid}` belgesini elle oluşturun. Alanlar:

   - `uid` (string): istek belgesindeki UID
   - `familyId` (string): `sefer-family`
   - `deviceId` (string): istek belgesindeki cihaz kimliği
   - `role` (string): istek belgesindeki `requestedRole`
   - `active` (boolean): `true`
   - `approvedAt` (timestamp): onay zamanı

   Yanlış role veya cihaz kimliğine onay vermeyin. Telefon kendi authorization belgesini yazamaz.
9. Telefonda **ONAYI KONTROL ET** düğmesine basın. Kurallar eşleşmeyi doğrular ve cihaz yalnız
   kendi rol projeksiyonunu oluşturur.
10. Bir cihazın erişimini kapatmak için authorization belgesindeki `active` alanını `false`
    yapın. Belgeyi doğrudan silmek de erişimi keser; önce fiziksel alarm cihazındaki son çalışan
    programın güvenli devrini planlayın.

App Check ücretsizdir fakat Play Integrity kurulumu ve gerçek cihaz doğrulaması tamamlanmadan
enforcement açılmamalıdır. Bu manuel UID + deviceId yetkilendirmesinin yerine geçmez.

Debug derlemesi, gerçek yapılandırma dosyası bulunsa bile yanlışlıkla canlı aile verisine yazmamak için yerel emülatörleri kullanır. Canlı Firebase doğrulaması release adayı ile ve özel test aile hesabıyla yapılmalıdır.

## Güvenlik sınırları

- Sadece oturum açmış olmak aile verisine erişim sağlamaz; Firestore üyelik kaydını doğrular.
- Kullanıcıya gösterilen pairing code veya e-posta/parola akışı yoktur.
- Her kurulum ayrı anonymous UID ve yerel deviceId kullanır; production'da bu ikili ve rol
  `deviceAuthorizations` belgesinde birebir onaylı değilse provisioning reddedilir.
- Alarm cihazları programı yalnız okur; yalnız kendi doz occurrence durumlarını yayımlar.
- Admin cihazları ilaç programını yönetebilir ancak alarm cihazı adına alınmış onayı yazamaz.
- `google-services.json`, servis hesabı anahtarları, UID/deviceId değerleri ve telefon numaraları
  loglanmaz veya Git'e eklenmez.
- Servis hesabı anahtarı Android uygulamasına hiçbir zaman konmaz.
