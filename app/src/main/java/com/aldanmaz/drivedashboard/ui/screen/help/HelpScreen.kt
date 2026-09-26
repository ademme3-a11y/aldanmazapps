package com.aldanmaz.drivedashboard.ui.screen.help

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.R
import com.aldanmaz.drivedashboard.ui.theme.DashboardPaletteRuntime

private data class HelpItem(
    val title: String,
    val description: String,
    val usage: String,
    @DrawableRes val icon: Int? = null,
    val badge: String = ""
)

private val helpItems = listOf(
    HelpItem(
        "ANA SÜRÜŞ EKRANI",
        "GPS hızını, rakımı, günlük kilometreyi, yakıtı, tahmini kalan menzili, saati ve seçili araç modunu birlikte gösterir. Park ekranındaki GÜN KM satırı kilometre başına tahmini yakıt maliyetini de gösterir. Bağlantı satırında aktif ağ, Bluetooth ve OBD cihazlarının adları ilk üç harfiyle yeşil gösterilir.",
        "Üst bardaki ikonlara dokunarak ilgili özelliği açın.",
        R.drawable.aldanmaz_drive_app_icon
    ),
    HelpItem(
        "CANLI YÜKSEKLİK",
        "Gün boyunca geçilen rakımları dağ profili üzerinde canlı olarak izler. Veriler gece yarısında sıfırlanır.",
        "LIVE ikonuna dokunun. Grafiği sürükleyerek geçmiş noktaları inceleyin.",
        R.drawable.icon_live
    ),
    HelpItem(
        "AY EVRESİ",
        "NASA'nın saatlik Ay görünümünü üst barda gösterir. Gündüz zemini mavi, gece siyah olur; internet yoksa son görüntü korunur.",
        "Üst bardaki Ay ikonuna dokunarak büyük görünümü, evre adını ve aydınlanma oranını açın.",
        badge = "AY"
    ),
    HelpItem(
        "ROTA PLANLAYICI",
        "Hedefi ve ara durakları belirler; otomobil, camper veya çekme karavana uygun rota hazırlamanıza yardım eder.",
        "Karavan ikonuna dokunun; hedefi yazarak veya mikrofonla söyleyerek ekleyin.",
        R.drawable.icon_caravan_route
    ),
    HelpItem(
        "NAVİGASYON",
        "Kayıtlı hedefi uyumlu navigasyon uygulamasında açar ve yol tarifini başlatır.",
        "Navigasyon ikonuna dokunun ve kullanmak istediğiniz uygulamayı seçin.",
        R.drawable.icon_navigation
    ),
    HelpItem(
        "EVE GİT",
        "Kayıtlı ev adresi için doğrudan rota oluşturur. Adres yoksa kayıt ekranını açar.",
        "Ev ikonuna dokunun; ilk kullanımda adresi kaydedin.",
        R.drawable.icon_home
    ),
    HelpItem(
        "TRAFİK AJANI",
        "Belirlenen hedef yönündeki trafik yoğunluğunu, gecikmeleri, yol çalışmasını ve kapanmaları kontrol eder.",
        "Radar ikonundan hedefi belirleyin ve Trafik Ajanını başlatın.",
        R.drawable.icon_radar
    ),
    HelpItem(
        "HIZ KORİDORU",
        "Beyaz kesik yol çizgili ORT HIZ KORİDORU alanı, koridor boyunca ortalama hızınızı hesaplar. Aktifken dijital saatin yanında turuncu hız değeri görünür.",
        "ORT HIZ KORİDORU alanına bir kez dokunarak başlatın; tekrar dokunarak kapatın.",
        badge = "50"
    ),
    HelpItem(
        "YOLCULUK İSTATİSTİKLERİ",
        "Gün, hafta, ay ve yıl kilometresi; yakıt alımı/harcaması ve hız aralığı dağılımlarını sürücüye göre gösterir. Geçmiş kayıtlar gece yarısında silinmez.",
        "GÜN görünümü 00.00'da yeni güne geçer. Devam eden sürüşün önceki güne ait bölümü kaydedilir ve yeni gün ayrı devam eder.",
        badge = "KM"
    ),
    HelpItem(
        "MULTİMEDYA",
        "YouTube, müzik, radyo, Chrome, Gemini Live ve ChatGPT özelliklerine ana ekrandan hızlı erişim sağlar.",
        "Gemini simgesine dokununca Live başlar ve siz açıkça kapatana kadar foreground service ile arka planda aktif kalır. Başka uygulamaya geçmek veya ALD Drive ekranını kapatıp açmak görüşmeyi bitirmez. Bildirimdeki Gemini'yi Kapat düğmesi veya sesli Gemini'yi kapat komutu oturumu tamamen sonlandırır. YouTube ve ChatGPT kendi uygulamasını, yüklü değilse web sayfasını açar.",
        badge = "▶"
    ),
    HelpItem(
        "YAKIT VE MALİYET",
        "Depodaki tahmini yakıtı, tüketilen litreyi ve TL maliyetini kayıtlı tüketim değerlerine göre hesaplar.",
        "Yakıt alanına dokunun; depo, tüketim ve yakıt alım bilgilerini doğru girin.",
        badge = "LT\nTL"
    ),
    HelpItem(
        "PARK EKRANI",
        "Araç durduğunda P göstergesini, park süresini ve günlük kilometreyi gösterir.",
        "Park modu otomatik açılır. Günlük kilometre gece 00.00'da sıfırlanır.",
        badge = "P"
    ),
    HelpItem(
        "PUSULA",
        "Yönü gösterir. Sensör bulunmayan araç ekranlarında hareket yönü GPS üzerinden tahmin edilir.",
        "Pusula ikonuna dokunarak büyük pusula ekranını açın.",
        R.drawable.icon_compass
    ),
    HelpItem(
        "SESLİ KOMUTLAR",
        "Hedef, trafik, medya, görünüm ve sürüş bilgilerini tek mikrofonla yönetir. Sabit girişler gerçek insan sesi, değişken rakamlar TTS ile okunur; önemli uyarılar öncelik kuyruğuna alınır.",
        "Ana ekrandaki Gemini simgesi tek global ses düğmesidir. Dokununca Gemini Live başlar; tekrar dokununca kapanır. Arka planda çalışırken kalıcı bildirim görünür. Eski yüzen mikrofon ve Hey Car arka plan dinleyicisi kaldırılmıştır.",
        badge = "SES"
    ),
    HelpItem(
        "HIZ / RAKIM KALİBRASYONU",
        "Araç kadranı ile GPS arasındaki hız farkını yalnız ekranda düzeltir; ham GPS mesafe ve park hesabında korunur. Rakım için ayrı metre düzeltmesi uygulanır.",
        "Ayarlar → Araç Sistemleri bölümünde hız için varsayılan +4 km/sa, rakım için varsayılan −25 m değerini gerektiğinde değiştirin.",
        badge = "±"
    ),
    HelpItem(
        "MÜZİK",
        "Seçtiğiniz müzik uygulamasını açar ve desteklenen oynatma kontrollerini kullanır.",
        "Müzik notasına dokunun; ilk kullanımda varsayılan uygulamayı seçin.",
        R.drawable.icon_music
    ),
    HelpItem(
        "RADYO",
        "Araç ekranında kurulu ve seçilmiş radyo uygulamasına hızlı erişim sağlar.",
        "Radyo ikonuna dokunarak açın; yeniden dokunarak veya geri ile kapatın.",
        R.drawable.icon_radio
    ),
    HelpItem(
        "OBD ARAÇ BİLGİLERİ",
        "ECU'nun desteklediği PID'leri tarar; motor, sıcaklık, hava/basınç, yakıt, voltaj, pedal ve tork verilerini gösterir. Kayıtlı, bekleyen ve kalıcı arıza kodlarını ayrı okur.",
        "OBD ikonunu açın, eşleştirilmiş ELM327 adaptörünü seçin ve bağlantıyı başlatın. Desteklenmeyen değerler açıkça belirtilir.",
        R.drawable.icon_obd
    ),
    HelpItem(
        "GECE & OLED GÖRÜNÜMÜ",
        "Gündüz, gece veya otomatik görünümü seçer. Gece yazıları BT etiketi tonunda gri olur; OLED seçeneği ana zeminleri gerçek siyaha çevirir ve yıldızlar gece görünür.",
        "Ayarlar → Görünüm & Saat bölümünden modu, parlaklığı ve OLED Siyah seçeneğini belirleyin.",
        badge = "OLED"
    ),
    HelpItem(
        "ÇEVRİMDIŞI HARİTALAR",
        "Google Haritalar'da indirdiğiniz alanlarda internet olmadığında otomobil rotasını kullanmanızı sağlar. Canlı trafik çevrimdışıyken alınamaz.",
        "Ayarlar → Araç Sistemleri → Çevrimdışı Haritalar bölümünden Google Haritalar'ı açın; Profil → Çevrimdışı haritalar → Kendi haritanızı seçin → İndir yolunu izleyin.",
        badge = "MAP"
    ),
    HelpItem(
        "NAMAZ VAKİTLERİ",
        "Konuma göre vakitleri 10 inç yatay ekranda gösterir; sıradaki vakit ve kalan süreyi öne çıkarır.",
        "Üst bardaki NAM ikonuna dokunun. Sol üstteki Geri ile ana ekrana dönün; Yenile ile konuma göre tekrar hesaplayın.",
        badge = "NAM"
    ),
    HelpItem(
        "ARAÇ MODU",
        "Otomobil, camper veya çekme karavan seçimine göre araç görselini ve ilgili sürüş modunu değiştirir.",
        "Ana ekrandaki araç görseline dokunun ve araç tipini seçin.",
        badge = "ARAÇ"
    ),
    HelpItem(
        "AYARLAR VE OTOMATİK AÇILIŞ",
        "Görünüm, üst bar, yakıt, ses, araç sistemleri ve uygulamanın araç açıldığında başlatılması burada yönetilir.",
        "Dişli ikonuna dokunun. Güvenlik nedeniyle bazı ayarlar yalnızca araç dururken değişir.",
        R.drawable.icon_settings
    )
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val accent = DashboardPaletteRuntime.accent
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (DashboardPaletteRuntime.isOled) Color.Black else Color(0xFF02070D))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "‹  GERİ",
                color = accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBack).padding(8.dp)
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("YARDIM & KULLANIM KILAVUZU", color = DashboardPaletteRuntime.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text("İkonu tanıyın • Ne yaptığını görün • Nasıl kullanılacağını öğrenin", color = Color(0xFF8FA6BA), fontSize = 9.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("?", color = accent, fontSize = 24.sp, fontWeight = FontWeight.Black)
        }

        Text(
            text = "Güvenliğiniz için ayrıntılı ayarları ve bu kılavuzu araç dururken kullanın.",
            color = Color(0xFFFFD982),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 390.dp),
            state = rememberLazyGridState(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(helpItems) { item -> HelpCard(item, accent) }
        }
    }
}

@Composable
private fun HelpCard(item: HelpItem, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (DashboardPaletteRuntime.isOled) Color.Black else Color(0xFF07121E)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .38f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFF020A12)),
                contentAlignment = Alignment.Center
            ) {
                if (item.icon != null) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(item.icon),
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(50.dp)
                    )
                } else {
                    Text(
                        text = item.badge,
                        color = if (item.badge == "A 50") Color(0xFFFF9D2E) else accent,
                        fontSize = if (item.badge.length > 4) 12.sp else 20.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 19.sp
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = 13.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(item.title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text(item.description, color = DashboardPaletteRuntime.primaryText, fontSize = 10.sp, lineHeight = 14.sp)
                Divider(color = Color(0xFF294052), thickness = 1.dp)
                Text("NASIL KULLANILIR?", color = Color(0xFF7FB9D1), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text(item.usage, color = Color(0xFF9CB0C0), fontSize = 9.sp, lineHeight = 12.sp)
            }
        }
    }
}
