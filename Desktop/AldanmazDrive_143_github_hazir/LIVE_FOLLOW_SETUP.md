# Aldanmaz Drive — güvenli canlı konum eşleştirmesi

Bu belge, `feature/bidirectional-live-follow` dalındaki Firebase canlı takip altyapısı içindir. Bu kurallar **henüz Firebase'e yayımlanmış değildir**.

## Güvenlik modeli

- Her kurulum Firebase Anonymous Authentication ile ayrı bir UID alır. İki cihazda aynı Google hesabının kullanılması gerekmez.
- Her cihaz yalnızca kendi `liveFollow/{uid}` kaydını yazabilir.
- Bir cihaz diğer cihazın konumunu ancak `pairedDevices/{hedefUid}/{okuyucuUid} = true` eşleştirme kaydı varsa okuyabilir.
- `pairedDevices` istemciler tarafından değiştirilemez; eşleştirmeyi yalnızca Firebase Console gibi güvenilir yönetim erişimi olan biri yapabilir.
- Veritabanı genel okuma/yazmaya açılmaz.

## Kurulum adımları

1. Firebase Console → Realtime Database → Rules bölümüne gidin.
2. Mevcut kuralları yedekleyin. Bu dosyadaki kuralları inceleyip **uygulama kodu ve UID eşleştirmesi hazır olmadan yayımlamayın**.
3. Her iki kurulumun Firebase Anonymous UID değerini uygulama içinden görünür hâle getiren eşleştirme ekranı henüz tamamlanmadığı için UID'leri tahmin etmeyin veya elle uydurmayın.
4. UID'ler güvenilir şekilde alındıktan sonra Realtime Database'de yönetici olarak şu iki kaydı oluşturun:
   - `pairedDevices/MEHMET_UID/NURDAN_UID = true`
   - `pairedDevices/NURDAN_UID/MEHMET_UID = true`
5. Ardından kuralları yayımlayıp iki yönlü okuma/yazmayı cihazlarda test edin.

## Önemli

Bu dosya bir güvenli kural taslağıdır; tek başına canlı takip özelliğini tamamlamaz. UID görüntüleme/eşleştirme arayüzü ve GPS gönderme/dinleme entegrasyonu doğrulanmadan canlı sinyal gösterilmemelidir. Eski konumlar canlı kabul edilmemeli; uygulama çevrimdışıysa sinyal durmalıdır.
