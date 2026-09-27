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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

data class ProductResult(val store:String,val title:String,val price:Double,val rating:Double?,val stock:Boolean,val url:String,val checks:List<String>)
data class Spec(val raw:String,val brand:String?,val size:Int?,val max:Double?,val minRating:Double)
data class Store(val name:String,val host:String,val search:(String)->String)

private val stores=listOf(
 Store("Trendyol","trendyol.com"){q->"https://www.trendyol.com/sr?q="+enc(q)},
 Store("Hepsiburada","hepsiburada.com"){q->"https://www.hepsiburada.com/ara?q="+enc(q)},
 Store("Amazon","amazon.com.tr"){q->"https://www.amazon.com.tr/s?k="+enc(q)},
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
   done(if(r.isEmpty())"Doğrulanmış uygun ürün bulunamadı.":r.size.toString()+" doğrulanmış ürün bulundu.",r)
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(){
 val a=LocalContext.current as MainActivity;val c=LocalContext.current
 var q by remember{mutableStateOf("")};var max by remember{mutableStateOf("")};var min by remember{mutableStateOf(4f)}
 var onlyStock by remember{mutableStateOf(true)};var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("Hazır")};var results by remember{mutableStateOf(emptyList<ProductResult>())};var spec by remember{mutableStateOf<Spec?>(null)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF1D4ED8))){
  Scaffold(topBar={CenterAlignedTopAppBar(title={Column(horizontalAlignment=Alignment.CenterHorizontally){Text("ALIŞVERİŞ AJANI",fontWeight=FontWeight.Black,letterSpacing=1.5.sp);Text("Canlı arama • doğrulanmış ürün",fontSize=11.sp)}},navigationIcon={Icon(Icons.Default.ShoppingCart,null,Modifier.padding(start=16.dp))})}){pad->
   LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF7F8FA)).padding(pad),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
    item{Card(shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     Text("Ne arıyorsunuz?",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
     OutlinedTextField(q,{q=it},Modifier.fillMaxWidth(),placeholder={Text("Örn. Samsung 65 inç TV")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
     Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
      OutlinedTextField(max,{max=it.filter{ch->ch.isDigit()||ch=='.'||ch==','}},Modifier.weight(1f),label={Text("Maks. TL")},singleLine=true)
      Button(enabled=q.isNotBlank()&&!busy,onClick={
       val mx=max.replace(".","").replace(",",".").toDoubleOrNull();val s=Engine.analyze(q,mx,min.toDouble());spec=s;busy=true
       a.search(s){msg,data->status=msg;results=if(onlyStock)data.filter{it.stock}else data;busy=false}
      },modifier=Modifier.height(56.dp),shape=RoundedCornerShape(16.dp)){Icon(Icons.Default.Search,null);Spacer(Modifier.width(5.dp));Text(if(busy)"TARANIYOR" else "ARA",fontWeight=FontWeight.Bold)}
     }
    }}}
    spec?.let{s->item{Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(14.dp)){
     Text("AJANIN ANLADIĞI İSTEK",fontWeight=FontWeight.Black,fontSize=12.sp)
     Text("Marka: "+(s.brand?:"belirtilmedi")+" • Ebat: "+(s.size?.toString()?.plus("\"")?:"belirtilmedi")+" • Maks: "+(s.max?.let{money(it)+" TL"}?:"belirtilmedi")+" • Puan ≥ "+("%.1f".format(s.minRating)))
    }}}}
    item{Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(16.dp)){
     Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Tune,null);Spacer(Modifier.width(8.dp));Text("Ajan filtreleri",fontWeight=FontWeight.Bold)}
     Row(verticalAlignment=Alignment.CenterVertically){Checkbox(onlyStock,{onlyStock=it});Text("Sadece stokta olanlar")}
     Text("Minimum puan: "+"%.1f".format(min));Slider(min,{min=it},valueRange=0f..5f,steps=9)
    }}}
    item{Text("Tarama kapsamı",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);Text(stores.size.toString()+" mağaza • demo veri yok • doğrulanamayan ürün yok",style=MaterialTheme.typography.bodySmall)}
    item{Text(status,fontWeight=FontWeight.Bold)}
    if(results.isNotEmpty()){item{Text("Uygun sonuçlar",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black)};items(results.take(10)){p->ResultCard(p){c.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(p.url)))}}}
    else if(spec!=null&&!busy){item{Card(shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.Verified,null,Modifier.size(38.dp));Spacer(Modifier.height(8.dp));Text("Doğrulanmış sonuç yok",fontWeight=FontWeight.Bold);Text("Marka, ebat, fiyat, puan ve gerçek ürün bağlantısı doğrulanamıyorsa ürün gösterilmez.",textAlign=TextAlign.Center,style=MaterialTheme.typography.bodySmall)}}}}
   }
  }
 }
}

@Composable private fun ResultCard(p:ProductResult,open:()->Unit){
 Card(shape=RoundedCornerShape(22.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Text(p.store,Modifier.padding(horizontal=10.dp,vertical=6.dp),fontSize=12.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.weight(1f));Text("● STOKTA",color=Color(0xFF15803D),fontWeight=FontWeight.Bold,fontSize=12.sp)}
  Text(p.title,fontWeight=FontWeight.Bold,maxLines=3,overflow=TextOverflow.Ellipsis);Text("✓ "+p.checks.joinToString("   ✓ "),style=MaterialTheme.typography.bodySmall)
  Row(verticalAlignment=Alignment.Bottom){Text(money(p.price)+" TL",fontSize=27.sp,fontWeight=FontWeight.Black);Spacer(Modifier.weight(1f));p.rating?.let{Text("★ %.1f".format(it),fontWeight=FontWeight.Bold)}}
  Button(open,Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text("ÜRÜNÜ GÖR",fontWeight=FontWeight.Bold);Spacer(Modifier.width(6.dp));Icon(Icons.Default.OpenInNew,null)}
 }}
}

private object Engine{
 private val brands=listOf("Samsung","LG","Sony","Philips","TCL","Xiaomi","Apple","Huawei","Lenovo","Asus","Acer","Bosch","Arçelik","Beko","Vestel","Dyson","Brita","Ariel","Persil","Omo")
 fun analyze(q:String,max:Double?,rating:Double):Spec{
  val n=norm(q);val b=brands.firstOrNull{n.contains(norm(it))}
  val z=Regex("(?i)(?:^|\\\\s)(\\\\d{2,3})\\\\s*(?:inç|inch|\\\")").find(q)?.groupValues?.get(1)?.toIntOrNull()
  return Spec(q.trim(),b,z,max,rating)
 }
 suspend fun search(s:Spec):List<ProductResult>{
  return stores.flatMap{st->runCatching{fetch(st,s)}.getOrDefault(emptyList())}.filter{it.stock&&it.price>0&&(s.max==null||it.price<=s.max)}.sortedBy{it.price}.distinctBy{norm(it.title)}
 }
 private fun fetch(st:Store,s:Spec):List<ProductResult>{
  val doc=Jsoup.connect(st.search(query(s))).userAgent(UA).timeout(15000).followRedirects(true).get();val out=mutableListOf<ProductResult>()
  doc.select("script[type=application/ld+json]").forEach{x->runCatching{parseJson(x.data(),st,out)}};if(out.isEmpty())anchors(doc,st,out)
  return out.mapNotNull{verify(it,s)}.take(20)
 }
 private fun query(s:Spec)=s.raw+(if(s.size!=null&&!norm(s.raw).contains(s.size.toString()))" "+s.size+" inç" else "")
 private fun verify(p:ProductResult,s:Spec):ProductResult?{
  if(s.brand!=null&&!norm(p.title).contains(norm(s.brand)))return null
  if(s.size!=null&&!Regex("(?i)(^|\\\\D)${s.size}\\\\s*(?:inç|inch|\\\"|inc)($|\\\\D)").containsMatchIn(p.title))return null
  if(p.rating==null||p.rating<s.minRating||!p.stock)return null
  if(s.max!=null&&p.price>s.max)return null
  if(!p.url.startsWith("https://")||p.url.endsWith("/")||p.url.contains("/arama")||p.url.contains("/sr?"))return null
  val checks=buildList{if(s.brand!=null)add("Marka "+s.brand);if(s.size!=null)add(s.size.toString()+"\"");add("Puan %.1f".format(p.rating));add("Fiyat doğrulandı")}
  return p.copy(checks=checks)
 }
 private fun parseJson(raw:String,st:Store,out:MutableList<ProductResult>){
  val x=raw.trim();if(x.startsWith("{"))walk(org.json.JSONObject(x),st,out)
  else if(x.startsWith("[")){val a=org.json.JSONArray(x);for(i in 0 until a.length())a.optJSONObject(i)?.let{walk(it,st,out)}}
 }
 private fun walk(o:org.json.JSONObject,st:Store,out:MutableList<ProductResult>){
  if(o.optString("@type").equals("Product",true)){
   val name=o.optString("name").trim();val url=o.optString("url").trim();val offers=o.optJSONObject("offers")
   val price=price(offers?.optString("price")?:offers?.optString("lowPrice")?:"")
   val rating=o.optJSONObject("aggregateRating")?.optString("ratingValue")?.replace(",",".")?.toDoubleOrNull()
   if(name.isNotBlank()&&url.isNotBlank()&&price!=null)out+=ProductResult(st.name,name,price,rating,true,absolute(st.host,url),emptyList())
  }
  val k=o.keys();while(k.hasNext()){when(val v=o.opt(k.next())){is org.json.JSONObject->walk(v,st,out);is org.json.JSONArray->for(i in 0 until v.length())v.optJSONObject(i)?.let{walk(it,st,out)}}}
 }
 private fun anchors(doc:org.jsoup.nodes.Document,st:Store,out:MutableList<ProductResult>){
  val rx=Regex("(?i)(\\d{1,3}(?:[. ]\\d{3})*(?:,\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)\\s*(?:TL|₺)")
  doc.select("a[href]").forEach{a->val title=a.text().trim();val href=a.absUrl("href");if(title.length<15||href.isBlank()||!href.contains(st.host)||href.endsWith("/")||href.contains("/arama")||href.contains("/sr?"))return@forEach
   var e:org.jsoup.nodes.Element=a;var txt=title;repeat(5){e=e.parent()?:return@repeat;if(e.text().length>txt.length)txt=e.text()}
   val m=rx.find(txt)?:return@forEach;val pr=price(m.groupValues[1])?:return@forEach;val r=Regex("(?<!\\d)([0-5](?:[.,]\\d))(?=\\s*(?:\\(|/|★|puan))").find(txt)?.groupValues?.get(1)?.replace(",",".")?.toDoubleOrNull()
   out+=ProductResult(st.name,title,pr,r,!txt.contains("tükendi",true)&&!txt.contains("stok yok",true),href,emptyList())
  }
 }
 private fun price(raw:String):Double?{val x=raw.trim().replace(" TL","").replace("₺","").replace(" ","");if(x.contains(","))return x.replace(".","").replace(",",".").toDoubleOrNull();val p=x.split(".");return if(p.size==2&&p[1].length==3)x.replace(".","").toDoubleOrNull()else x.toDoubleOrNull()}
 private fun absolute(host:String,u:String)=if(u.startsWith("http"))u else if(u.startsWith("/"))"https://"+host+u else "https://"+host+"/"+u
 private fun norm(x:String)=x.lowercase(Locale("tr","TR")).replace("ı","i").replace("ş","s").replace("ğ","g").replace("ü","u").replace("ö","o").replace("ç","c").replace(Regex("\\s+")," ").trim()
}
private const val UA="Mozilla/5.0 (Linux; Android 16; SM-A256B) AppleWebKit/537.36 Chrome/150 Mobile Safari/537.36"
private fun enc(s:String)=URLEncoder.encode(s,"UTF-8")
private fun money(v:Double)="%,.2f".format(v).replace(",","X").replace(".",",").replace("X",".")
