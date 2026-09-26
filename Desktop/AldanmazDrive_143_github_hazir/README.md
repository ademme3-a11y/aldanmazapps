# Aldanmaz Drive

Android için geliştirilmiş bir sürüş / araç gösterge paneli (dashboard) uygulaması. Jetpack Compose, Firebase ve TomTom yol/hız limiti API'si kullanılarak yazılmıştır.

- **Package (applicationId):** `com.aldanmaz.drivedashboard`
- **Min SDK:** 29 · **Target SDK:** 37
- **Dil:** Kotlin, Jetpack Compose

## Kurulum

1. Bu repoyu klonlayın:
   ```bash
   git clone <bu-reponun-url'si>
   ```
2. Proje kök dizininde bir `local.properties` dosyası oluşturun (bu dosya `.gitignore` ile hariç tutulur, repoya dahil edilmez) ve şunları ekleyin:
   ```properties
   sdk.dir=/Android/sdk/yolunuz
   TOMTOM_API_KEY=kendi_tomtom_api_anahtarınız
   ```
3. `app/google-services.json` dosyasının mevcut olduğundan emin olun (Firebase Console > Proje Ayarları > Uygulamalarınız bölümünden indirilebilir). Bu dosya repoya dahildir; başka bir Firebase projesi kullanacaksanız kendi dosyanızla değiştirin.
   > Not: `app/build.gradle.kts` içinde, bu dosya bulunamazsa geliştiricinin kendi bilgisayarındaki başka proje klasörlerinden (`AldanmazDrive_99`, `AldanmazDrive_72` vb.) otomatik kopyalamaya çalışan bir yedek mekanizma var. Bu yollar yalnızca orijinal geliştiricinin makinesinde anlamlıdır; başka bir bilgisayarda çalışmaz ve dosya elle sağlanmalıdır.
4. Android Studio ile açın veya komut satırından derleyin:
   ```bash
   ./gradlew assembleDebug
   ```

## Notlar

- `local.properties`, `*.jks`/`*.keystore` (imzalama anahtarları) ve build çıktıları `.gitignore` ile repo dışında tutulur.
- `google-services.json` içindeki Android API anahtarı, Firebase tarafında paket adı + imza (SHA-1) kısıtlamasıyla korunur. Yine de repoyu herkese açık (public) yapacaksanız Firebase Console'da API kısıtlamalarını gözden geçirmeniz önerilir.
