package com.aldanmaz.shoppingagent

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.Locale

data class ProductResult(val store:String,val title:String,val price:Double,val rating:Double?,val stock:Boolean,val url:String,val checks:List<String>)
data class Spec(val raw:String,val brand:String?,val sizeText:String?,val max:Double?,val minRating:Double)
data class Store(val name:String,val host:String,val search:(String)->String)

private val stores=listOf(
 Store("Trendyol","trendyol.com"){q->"https://www.trendyol.com/sr?q="+enc(q)},
 Store("Hepsiburada","hepsiburada.com"){q->"https://www.hepsiburada.com/ara?q="+enc(q)},
 Store("Amazon Türkiye","amazon.com.tr"){q->"https://www.amazon.com.tr/s?k="+enc(q)},
 Store("n11","n11.com"){q->"https://www.n11.com/arama?q="+enc(q)},
 Store("Pazarama","pazarama.com"){q->"https://www.pazarama.com/arama?q="+enc(q)},
 Store("MediaMarkt","mediamarkt.com.tr"){q->"https://www.mediamarkt.com.tr/tr/search.html?query="+enc(q)},
 Store("Teknosa","teknosa.com"){q->"https://www.teknosa.com/arama/?s="+enc(q)}
)

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b);setContent{App()}}
 fun search(s:Spec,done:(String,List<ProductResult>)->Unit){
  lifecycleScope.launch{
   done("Canlı mağazalar taranıyor…",emptyList())
   val r=withContext(Dispatchers.IO){Engine.search(s)}
   done(if(r.isEmpty())"Doğrulanmış uygun ürün bulunamadı." else r.size.toString()+" doğrulanmış ürün bulundu.",r)
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(){
 val a=LocalContext.current as MainActivity
 val c=LocalContext.current
 var q by remember{mutableStateOf("")};var brandInput by remember{mutableStateOf("")};var sizeInput by remember{mutableStateOf("")}
 var max by remember{mutableStateOf("")};var min by remember{mutableStateOf(4f)};var onlyStock by remember{mutableStateOf(true)}
 var busy by remember{mutableStateOf(false)};var status by remember{mutableStateOf("Hazır")}
 var results by remember{mutableStateOf(emptyList<ProductResult>())};var spec by remember{mutableStateOf<Spec?>(null)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF1D4ED8))){
  Scaffold(topBar={CenterAlignedTopAppBar(
   title={Column(horizontalAlignment=Alignment.CenterHorizontally){Text("ALIŞVERİŞ AJANI",fontWeight=FontWeight.Black,letterSpacing=1.5.sp);Text("Canlı arama • gerçek ürün/fiyat doğrulama",fontSize=11.sp)}},
   navigationIcon={Icon(Icons.Default.ShoppingCart,null,Modifier.padding(start=16.dp))}
  )}){pad->
   LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(pad),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Card(shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("Ne arıyorsunuz?",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
     OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),placeholder={Text("Örn. Samsung TV veya 5 lt zeytinyağı")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
      OutlinedTextField(brandInput,{brandInput=it},Modifier.weight(1f),label={Text("Marka")},placeholder={Text("Samsung")},singleLine=true)
      OutlinedTextField(sizeInput,{sizeInput=it},Modifier.weight(1f),label={Text("Ebat / Miktar")},placeholder={Text("65 inç / 5 lt / 500 gr")},singleLine=true)
     }
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
      OutlinedTextField(max,{max=it.filter{ch->ch.isDigit()||ch=='.'||ch==','}},Modifier.weight(1f),label={Text("Maks. TL")},singleLine=true)
      Button(enabled=(q.isNotBlank()||brandInput.isNotBlank()||sizeInput.isNotBlank())&&!busy,onClick={
       val mx=parseUserMoney(max);val s=Engine.analyze(q,brandInput,sizeInput,mx,min.toDouble());spec=s;busy=true;results=emptyList()
       a.search(s){msg,data->status=msg;results=if(onlyStock)data.filter{it.stock}else data;busy=false}
      },modifier=Modifier.height(56.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Default.Search,null);Spacer(Modifier.width(5.dp));Text(if(busy)"TARANIYOR" else "ARA",fontWeight=FontWeight.Bold)}
     }
    }}}
    spec?.let{s->item{Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(14.dp)){
     Text("AJANIN ANLADIĞI İSTEK",fontWeight=FontWeight.Black,fontSize=12.sp)
     Text("Marka: "+(s.brand?:"belirtilmedi")+" • Ebat/Miktar: "+(s.sizeText?:"belirtilmedi")+" • Maks: "+(s.max?.let{money(it)+" TL"}?:"belirtilmedi")+" • Puan ≥ "+"%.1f".format(s.minRating))
    }}}}
    item{Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Tune,null);Spacer(Modifier.width(8.dp));Text("Ajan filtreleri",fontWeight=FontWeight.Bold)}
     Row(verticalAlignment=Alignment.CenterVertically){Checkbox(onlyStock,{onlyStock=it});Text("Sadece stokta olanlar")}
     Text("Minimum puan: "+"%.1f".format(min));Slider(min,{min=it},valueRange=0f..5f,steps=9)
    }}}
    item{Text("Tarama kapsamı",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text("7 mağaza • sonuçlar gerçek ürün sayfasından doğrulanır",style=MaterialTheme.typography.bodySmall)}
    item{Text(status,fontWeight=FontWeight.Bold)}
    if(results.isNotEmpty()){item{Text("Uygun sonuçlar",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)};items(results.take(20)){p->ResultCard(p){c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(p.url)))}}}
    else if(spec!=null&&!busy){item{Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
     Icon(Icons.Default.Verified,null,Modifier.size(38.dp));Spacer(Modifier.height(8.dp));Text("Doğrulanmış sonuç yok",fontWeight=FontWeight.Bold)
     Text("Marka, ebat/miktar, güncel satış fiyatı, puan, stok ve gerçek ürün bağlantısı birlikte doğrulanmadan ürün gösterilmez.",textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)
    }}}}
   }
  }
 }
}

@Composable private fun ResultCard(p:ProductResult,open:()->Unit){
 Card(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Text(p.store,Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=12.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.weight(1f));Text(if(p.stock)"● STOKTA" else "● STOK YOK",color=if(p.stock)Color(0xFF15803D) else Color(0xFFB91C1C),fontWeight=FontWeight.Bold,fontSize=12.sp)}
  Text(p.title,fontWeight=FontWeight.Bold,maxLines=3,overflow=TextOverflow.Ellipsis);Text("✓ "+p.checks.joinToString("   ✓ "),style=MaterialTheme.typography.bodySmall)
  Row(verticalAlignment=Alignment.Bottom){Text(money(p.price)+" TL",fontSize=27.sp,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));p.rating?.let{Text("★ %.1f".format(it),fontWeight=FontWeight.Bold)}}
  Button(open,Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text("ÜRÜNÜ GÖR",fontWeight=FontWeight.Bold);Spacer(Modifier.width(6.dp));Icon(Icons.Default.OpenInNew,null)}
 }}
}

private object Engine{
 private val brands=listOf("Samsung","LG","Sony","Philips","TCL","Xiaomi","Apple","Huawei","Lenovo","Asus","Acer","Bosch","Arçelik","Beko","Vestel","Dyson","Brita","Ariel","Persil","Omo","Finish","Fairy")
 private val unitRx=Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*(lt|l|litre|litres|ml|kg|g|gr|gram|adet|ad|inç|inch|cm|mm|metre|m)")
 fun analyze(q:String,brandInput:String,sizeInput:String,max:Double?,rating:Double):Spec{
  val b=brandInput.trim().takeIf{it.isNotBlank()}?:brands.firstOrNull{norm(q).contains(norm(it))}
  val typed=sizeInput.trim().takeIf{it.isNotBlank()};val detected=unitRx.find(q)?.value
  return Spec(q.trim(),b,typed?:detected,max,rating)
 }
 suspend fun search(s:Spec):List<ProductResult>=coroutineScope{
  stores.map{st->async(Dispatchers.IO){runCatching{fetch(st,s)}.getOrDefault(emptyList())}}.awaitAll().flatten()
   .filter{it.stock&&it.price>0&&(s.max==null||it.price<=s.max)}.sortedBy{it.price}.distinctBy{norm(it.store+"|"+it.title)}
 }
 private fun fetch(st:Store,s:Spec):List<ProductResult>{
  val out=mutableListOf<ProductResult>()
  val queries=listOf(query(s),listOfNotNull(s.brand,s.sizeText).joinToString(" ")).filter{it.isNotBlank()}.distinct()
  for(q in queries){val doc=load(st.search(q))?:continue;parseSearchDocument(doc,st,out);if(out.isNotEmpty())break}
  if(out.isEmpty())for(candidate in engineCandidates(st,s).take(8))runCatching{val pd=load(candidate)?:return@runCatching;productFromDocument(pd,st,candidate)?.let{out+=it}}
  return out.mapNotNull{verifyAndRefresh(it,s)}.distinctBy{it.url}.take(20)
 }
 private fun parseSearchDocument(doc:Document,st:Store,out:MutableList<ProductResult>){doc.select("script[type=application/ld+json]").forEach{x->runCatching{parseJson(x.data(),st,out)}};anchors(doc,st,out)}
 private fun productFromDocument(doc:Document,st:Store,url:String):ProductResult?{
  val temp=mutableListOf<ProductResult>();doc.select("script[type=application/ld+json]").forEach{x->runCatching{parseJson(x.data(),st,temp)}}
  return temp.firstOrNull{isProductUrl(it.url)}?:runAnchorProduct(doc,st,url)
 }
 private fun runAnchorProduct(doc:Document,st:Store,url:String):ProductResult?{
  val title=doc.select("meta[property=og:title]").attr("content").ifBlank{doc.title()}.trim();if(title.isBlank())return null
  val price=extractCurrentPrice(doc,st)?:return null;return ProductResult(st.name,title,price,extractRating(doc),extractStock(doc),url,emptyList())
 }
 private fun verifyAndRefresh(p:ProductResult,s:Spec):ProductResult?{
  if(!isProductUrl(p.url))return null
  if(s.brand!=null&&!norm(p.title).contains(norm(s.brand)))return null
  if(s.sizeText!=null&&!matchesAmount(p.title,s.sizeText))return null
  if(p.rating==null||p.rating<s.minRating||!p.stock)return null
  val refreshed=runCatching{val d=load(p.url);if(d!=null){val current=extractCurrentPrice(d,stores.firstOrNull{it.name==p.store});val rr=extractRating(d)?:p.rating;val ss=extractStock(d);if(current!=null&&current>0)p.copy(price=current,rating=rr,stock=ss) else p.copy(rating=rr,stock=ss)}else p}.getOrDefault(p)
  if(!refreshed.stock||refreshed.rating==null||refreshed.rating<s.minRating)return null
  if(s.max!=null&&refreshed.price>s.max)return null
  val checks=buildList{if(s.brand!=null)add("Marka "+s.brand);if(s.sizeText!=null)add("Ebat/Miktar "+s.sizeText);add("Puan %.1f".format(refreshed.rating));add("Güncel fiyat");add("Stok")}
  return refreshed.copy(checks=checks)
 }
 private fun matchesAmount(title:String,wanted:String):Boolean{
  val w=unitRx.find(wanted)?:return norm(title).contains(norm(wanted));val num=w.groupValues[1].replace(",",".").toDoubleOrNull()?:return false;val unit=canonicalUnit(w.groupValues[2])
  for(m in unitRx.findAll(norm(title))){val n=m.groupValues[1].replace(",",".").toDoubleOrNull()?:continue;if(sameAmount(num,unit,n,canonicalUnit(m.groupValues[2])))return true}
  return false
 }
 private fun sameAmount(a:Double,au:String,b:Double,bu:String):Boolean{
  if(au==bu)return kotlin.math.abs(a-b)<0.0001
  val mlA=when(au){"l"->a*1000;"ml"->a;else->Double.NaN};val mlB=when(bu){"l"->b*1000;"ml"->b;else->Double.NaN};if(!mlA.isNaN()&&!mlB.isNaN())return kotlin.math.abs(mlA-mlB)<0.0001
  val gA=when(au){"kg"->a*1000;"g"->a;else->Double.NaN};val gB=when(bu){"kg"->b*1000;"g"->b;else->Double.NaN};if(!gA.isNaN()&&!gB.isNaN())return kotlin.math.abs(gA-gB)<0.0001
  return false
 }
 private fun canonicalUnit(u:String)=when(u.lowercase(Locale("tr","TR"))){
  "lt","l","litre","litres"->"l";"ml"->"ml";"kg"->"kg";"g","gr","gram"->"g";"inç","inch"->"inç";else->u.lowercase(Locale("tr","TR"))
 }
 private fun query(s:Spec):String{val p=mutableListOf<String>();s.brand?.let{p+=it};s.sizeText?.let{p+=it};if(s.raw.isNotBlank())p+=s.raw;return p.distinct().joinToString(" ")}
 private fun engineCandidates(st:Store,s:Spec):List<String>{
  val raws=listOf(query(s)+" site:"+st.host,listOfNotNull(s.brand,s.sizeText).joinToString(" ")+" site:"+st.host).filter{it.isNotBlank()}.distinct();val urls=mutableListOf<String>()
  for(raw in raws){val q=URLEncoder.encode(raw,"UTF-8");for(e in listOf("https://www.bing.com/search?q="+q,"https://www.google.com/search?q="+q)){val d=runCatching{Jsoup.connect(e).userAgent(UA).timeout(10000).followRedirects(true).get()}.getOrNull()?:continue;d.select("a[href]").forEach{a->{val u=a.absUrl("href");if(u.startsWith("https://")&&u.contains(st.host,true)&&isProductUrl(u))urls+=u}}}}
  return urls.distinct()
 }
 private fun parseJson(raw:String,st:Store,out:MutableList<ProductResult>){val x=raw.trim();if(x.startsWith("{"))runCatching{walk(org.json.JSONObject(x),st,out)}else if(x.startsWith("["))runCatching{val a=org.json.JSONArray(x);for(i in 0 until a.length())a.optJSONObject(i)?.let{walk(it,st,out)}}}
 private fun walk(o:org.json.JSONObject,st:Store,out:MutableList<ProductResult>){
  if(o.optString("@type").contains("Product",true)){
   val name=o.optString("name").trim();val url=o.optString("url").trim();val rating=o.optJSONObject("aggregateRating")?.optString("ratingValue")?.replace(",",".")?.toDoubleOrNull();val stock=o.optJSONObject("offers")?.let{offerAvailability(it)}?:true;val price=offerPrices(o.opt("offers")).firstOrNull()
   if(name.isNotBlank()&&url.isNotBlank()&&price!=null)out+=ProductResult(st.name,name,price,rating,stock,absolute(st.host,url),emptyList())
  }
  val k=o.keys();while(k.hasNext())when(val v=o.opt(k.next())){is org.json.JSONObject->walk(v,st,out);is org.json.JSONArray->for(i in 0 until v.length())v.optJSONObject(i)?.let{walk(it,st,out)}}
 }
 private fun offerPrices(v:Any?):List<Double>{val out=mutableListOf<Double>();fun add(o:org.json.JSONObject){listOf("price","priceAmount").forEach{key->parsePrice(o.optString(key))?.let{if(it>0)out+=it}}};when(v){is org.json.JSONObject->add(v);is org.json.JSONArray->for(i in 0 until v.length())v.optJSONObject(i)?.let{add(it)}};return out.distinct()}
 private fun offerAvailability(o:org.json.JSONObject):Boolean{val a=o.optString("availability").lowercase();return a.isBlank()||(!a.contains("outofstock")&&!a.contains("soldout")&&!a.contains("unavailable"))}
 private fun anchors(doc:Document,st:Store,out:MutableList<ProductResult>){
  val rx=Regex("(?i)(\\d{1,3}(?:[. ]\\d{3})*(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*(?:TL|₺)")
  doc.select("a[href]").forEach{a->{val title=a.text().trim();val href=a.absUrl("href");if(title.length<15||href.isBlank()||!href.contains(st.host,true)||!isProductUrl(href))return@forEach;var e:org.jsoup.nodes.Element=a;var txt=title;repeat(6){e=e.parent()?:return@repeat;if(e.text().length>txt.length&&e.text().length<1200)txt=e.text()};val m=rx.find(txt)?:return@forEach;val pr=parsePrice(m.groupValues[1])?:return@forEach;val r=Regex("(?<!\\d)([0-5](?:[.,]\\d))(?=\\s*(?:\\(|/|★|puan))").find(txt)?.groupValues?.get(1)?.replace(",",".")?.toDoubleOrNull();out+=ProductResult(st.name,title,pr,r,!txt.contains("tükendi",true)&&!txt.contains("stok yok",true),href,emptyList())}}
 }
 private fun extractCurrentPrice(doc:Document,st:Store?):Double?{
  val selectors=when(st?.name){
   "Trendyol"->listOf(".prc-dsc","[data-testid*=price]","[class*=price]")
   "Hepsiburada"->listOf("[data-test-id=price-current]","[data-testid*=price]","[class*=price]")
   "Amazon Türkiye"->listOf(".a-price .a-offscreen")
   "n11"->listOf(".newPrice",".new-price","[class*=price]")
   "Pazarama"->listOf("[class*=salePrice]","[class*=sale-price]","[class*=price]")
   "MediaMarkt"->listOf("[class*=price]","[data-test*=price]")
   "Teknosa"->listOf("[class*=price]","[data-testid*=price]")
   else->emptyList()
  }
  for(sel in selectors){val values=doc.select(sel).flatMap{listOf(it.attr("content"),it.text(),it.attr("aria-label"))}.mapNotNull{extractMoney(it)}.filter{it>0};if(values.isNotEmpty())return chooseVisiblePrice(values)}
  doc.select("meta[itemprop=price],meta[property=product:price:amount]").mapNotNull{extractMoney(it.attr("content"))}.firstOrNull()?.let{return it}
  val json=mutableListOf<ProductResult>();doc.select("script[type=application/ld+json]").forEach{x->runCatching{parseJson(x.data(),st?:stores.first(),json)}};return json.firstOrNull()?.price
 }
 private fun chooseVisiblePrice(values:List<Double>):Double=values.distinct().minOrNull()?:values.first()
 private fun extractRating(doc:Document):Double?{doc.select("meta[itemprop=ratingValue]").attr("content").replace(",",".").toDoubleOrNull()?.let{return it};val t=doc.select("[class*=rating],[class*=review]").text();return Regex("(?<!\\d)([0-5](?:[.,]\\d))(?:\\s*/\\s*5|\\s*puan|★)").find(t)?.groupValues?.get(1)?.replace(",",".")?.toDoubleOrNull()}
 private fun extractStock(doc:Document):Boolean{val t=doc.text().lowercase(Locale("tr","TR"));return !listOf("stokta yok","tükendi","ürün mevcut değil","out of stock","currently unavailable").any{t.contains(it)}}
 private fun extractMoney(text:String):Double?{val t=text.replace("\u00A0"," ").trim();val m=Regex("(?i)(\\d{1,3}(?:[. ]\\d{3})*(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*(?:TL|₺|try)").find(t)?:Regex("(?<!\\d)(\\d{1,3}(?:[. ]\\d{3})*(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)(?!\\d)").find(t)?:return null;return parsePrice(m.groupValues[1])}
 private fun parsePrice(raw:String):Double?{val x=raw.trim().replace("TL","",true).replace("₺","").replace(" ","");if(x.contains(","))return x.replace(".","").replace(",",".").toDoubleOrNull();val parts=x.split(".");return if(parts.size==2&&parts[1].length==3)x.replace(".","").toDoubleOrNull() else x.toDoubleOrNull()}
 private fun isProductUrl(u:String):Boolean{if(!u.startsWith("https://")||u.endsWith("/"))return false;val bad=listOf("/arama","/search","/sr?","?q=","/kategori","/category","/magaza","/s?","/liste");return bad.none{u.contains(it,true)}}
 private fun load(url:String):Document?=runCatching{Jsoup.connect(url).userAgent(UA).timeout(12000).followRedirects(true).referrer("https://www.google.com/").get()}.getOrNull()
 private fun absolute(host:String,u:String)=if(u.startsWith("http"))u else if(u.startsWith("/"))"https://"+host+u else "https://"+host+"/"+u
 private fun norm(x:String)=x.lowercase(Locale("tr","TR")).replace("ı","i").replace("ş","s").replace("ğ","g").replace("ü","u").replace("ö","o").replace("ç","c").replace(Regex("\\s+")," ").trim()
}
private const val UA="Mozilla/5.0 (Linux; Android 16; SM-A256B) AppleWebKit/537.36 Chrome/150 Mobile Safari/537.36"
private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
private fun parseUserMoney(s:String):Double?=s.trim().replace(".","").replace(",",".").toDoubleOrNull()
private fun money(v:Double)="%,.2f".format(v).replace(",","X").replace(".",",").replace("X",".")
