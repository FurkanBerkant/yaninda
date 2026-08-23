# Firebase aile bağlantısı kurulumu

Bu belge yalnızca aile senkronizasyonunu kurar. Dede telefonundaki ilaç alarmı Firebase, FCM veya internet üzerinden tetiklenmez; alarmın kaynağı yerel Room + AlarmManager yoludur.

## Yerel geliştirme

Debug uygulaması `demo-yaninda` projesiyle yerel Firebase Auth ve Firestore emülatörlerine bağlanır. Android emülatörü bilgisayara `10.0.2.2` üzerinden erişir.

1. Bilgisayarda desteklenen bir Node.js LTS sürümü (20 veya 22) ve Java 21 kurulu olmalı.
2. Repository root'unda `npm install` çalıştırın.
3. Auth ve Firestore testlerini `npm run test:firebase` ile çalıştırın.
4. Uygulamayı emülatörle elle denemek için ayrı Terminal penceresinde şunu açık bırakın:

   ```bash
   npx firebase emulators:start --only auth,firestore --project demo-yaninda
   ```

5. Android Studio'da `app` yapılandırmasını çalıştırın. İlk ekranda cihaz rolünü seçin.

Yerel emülatör kapalıysa aile işlemleri ağ hatası gösterir; yerel ilaç alarmı çalışmaya devam eder.

## Gerçek Firebase projesi

1. Firebase Console'da özel bir proje oluşturun.
2. Authentication bölümünde Email/Password ve Anonymous sağlayıcılarını etkinleştirin.
3. Firestore'u production mode ile oluşturun. Açık geliştirme kuralı kullanmayın.
4. Android uygulaması ekleyin; package adı tam olarak `com.berkant.yaninda` olmalı.
5. İndirilen gerçek `google-services.json` dosyasını `app/google-services.json` konumuna koyun. Bu dosya `.gitignore` içindedir; repository'ye eklemeyin.
6. `firebase use` ile doğru projeyi seçtikten sonra önce emülatör testlerini çalıştırın, sonra kuralları kontrollü biçimde dağıtın:

   ```bash
   npm run test:firebase
   npx firebase deploy --only firestore:rules,firestore:indexes
   ```

Debug derlemesi, gerçek yapılandırma dosyası bulunsa bile yanlışlıkla canlı aile verisine yazmamak için yerel emülatörleri kullanır. Canlı Firebase doğrulaması release adayı ile ve özel test aile hesabıyla yapılmalıdır.

## Güvenlik sınırları

- Sadece oturum açmış olmak aile verisine erişim sağlamaz; Firestore üyelik kaydını doğrular.
- Eşleştirme kodu 16 karakter, 15 dakika geçerli ve tek kullanımlıdır.
- Dede telefonu anonim ama aileye eşleştirilmiş dar yetkili bir cihaz oturumu kullanır.
- Aile telefonları ilaç programını uzaktan değiştiremez.
- `google-services.json`, servis hesabı anahtarları, kullanıcı e-postaları ve eşleştirme kodları loglanmaz veya Git'e eklenmez.
- Servis hesabı anahtarı Android uygulamasına hiçbir zaman konmaz.
