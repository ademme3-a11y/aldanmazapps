package com.aldanmaz.drivedashboard.ui.screen.drive

import android.graphics.Color as AndroidColor
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import java.util.Locale

@Composable
internal fun DriveNavigation3DScreen(
    latitude: Double?, longitude: Double?, speedKmh: Int, currentTime: String,
    vehicleKm: Double, headingDegrees: Float?, direction: String,
    locationStatus: String, speedLimitKmh: Int?, isGpsActive: Boolean, onBack: () -> Unit
) {
    val context = LocalContext.current
    var destination by remember { mutableStateOf("") }
    var mapView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(latitude, longitude, headingDegrees, speedKmh, speedLimitKmh) {
        val lat = latitude ?: return@LaunchedEffect
        val lon = longitude ?: return@LaunchedEffect
        val heading = headingDegrees ?: 0f
        mapView?.evaluateJavascript(
            "window.updateVehicle(" + lat + "," + lon + "," + heading + "," + speedKmh.coerceAtLeast(0) + "," + (speedLimitKmh ?: 0) + ");", null
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.rgb(12, 20, 29))
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadDataWithBaseURL("https://tiles.openfreemap.org/", DRIVE_3D_HTML, "text/html", "UTF-8", null)
                    mapView = this
                }
            },
            update = { mapView = it }
        )
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color(0xD9162532)).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Geri", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text("3D SÜRÜŞ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Beyaz Dacia Sandero Stepway • 07 MTU 93", color = Color(0xFFCAD6E0), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(currentTime, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(if (isGpsActive) "GPS AKTİF" else "GPS BEKLENİYOR", color = if (isGpsActive) Color(0xFF52E69A) else Color(0xFFFFC45C), fontSize = 10.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.weight(1f), color = Color(0xD9273949), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(speedKmh.coerceAtLeast(0).toString(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text(" km/h", color = Color(0xFFCAD6E0), fontSize = 11.sp, modifier = Modifier.padding(bottom = 5.dp))
                    }
                }
                DriveInfoChip("ARAÇ KM", if (vehicleKm > 0) String.format(Locale("tr", "TR"), "%,.0f", vehicleKm) else "—")
                DriveInfoChip("YÖN", direction.ifBlank { "--" })
                if (speedLimitKmh != null && speedLimitKmh > 0) DriveInfoChip("SINIR", speedLimitKmh.toString())
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = destination, onValueChange = { destination = it }, modifier = Modifier.weight(1f),
                    singleLine = true, placeholder = { Text("Varış noktası ara…", color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF28C8ED), unfocusedBorderColor = Color(0xFF728493),
                        cursorColor = Color.White
                    )
                )
                Button(onClick = {
                    val q = org.json.JSONObject.quote(destination.trim())
                    if (destination.isNotBlank()) mapView?.evaluateJavascript("window.findDestination($q);", null)
                }) { Icon(Icons.Default.Search, contentDescription = "Rota ara") }
            }
        }
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
            color = Color(0xD9162532), shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                if (latitude != null && longitude != null) "Canlı GPS izi açık • Hedef girilmezse geçtiğin yol çizilir" else locationStatus,
                Modifier.padding(11.dp), color = Color.White, fontSize = 11.sp
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
            mapView = null
        }
    }
}

@Composable
private fun DriveInfoChip(label: String, value: String) {
    Surface(color = Color(0xD9273949), shape = RoundedCornerShape(9.dp)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFFCAD6E0), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val DRIVE_3D_HTML = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.css">
<style>html,body,#map{margin:0;width:100%;height:100%;background:#101a24;overflow:hidden}.car{width:54px;height:90px;filter:drop-shadow(0 4px 5px #0009)}.car svg{width:100%;height:100%}.plate{position:absolute;bottom:7px;left:50%;transform:translateX(-50%);background:white;color:#111;font:bold 7px Arial;padding:2px 3px;border:1px solid #222;white-space:nowrap}.maplibregl-ctrl-attrib{display:none!important}</style></head><body><div id="map"></div>
<script src="https://unpkg.com/maplibre-gl@4.7.1/dist/maplibre-gl.js"></script><script>
var map,carMarker,trail=[],pos=[30.70,36.89],bearing=0;
var car=document.createElement('div');car.className='car';car.innerHTML='<svg viewBox="0 0 60 100"><path d="M15 18 Q18 5 30 5 Q42 5 45 18 L53 76 Q52 91 30 95 Q8 91 7 76Z" fill="#f7f8f9" stroke="#65717a" stroke-width="2"/><path d="M17 24 Q30 15 43 24 L46 51 Q30 58 14 51Z" fill="#243746" stroke="#cbd4da" stroke-width="2"/><path d="M13 60 Q30 66 47 60 L49 78 Q30 88 11 78Z" fill="#e8edf0"/><rect x="9" y="29" width="5" height="12" rx="2" fill="#fff0b0"/><rect x="46" y="29" width="5" height="12" rx="2" fill="#fff0b0"/><rect x="10" y="78" width="7" height="6" fill="#e33"/><rect x="43" y="78" width="7" height="6" fill="#e33"/></svg><div class="plate">07 MTU 93</div>';
map=new maplibregl.Map({container:'map',style:'https://tiles.openfreemap.org/styles/liberty',center:pos,zoom:15.5,pitch:62,bearing:0,antialias:true});
map.addControl(new maplibregl.NavigationControl({showCompass:false}),'bottom-right');
map.on('load',function(){
 var layers=map.getStyle().layers,label=null;
 for(var i=0;i<layers.length;i++){if(layers[i].type==='symbol'&&layers[i].layout&&layers[i].layout['text-field']){label=layers[i].id;break;}}
 map.addLayer({id:'3d-buildings',source:'openmaptiles','source-layer':'building',type:'fill-extrusion',minzoom:14,paint:{'fill-extrusion-color':'#8295a5','fill-extrusion-height':['coalesce',['get','render_height'],4],'fill-extrusion-base':['coalesce',['get','render_min_height'],0],'fill-extrusion-opacity':0.8}},label);
 map.addSource('gps-trail',{type:'geojson',data:{type:'Feature',geometry:{type:'LineString',coordinates:[]}}});
 map.addLayer({id:'gps-trail-line',type:'line',source:'gps-trail',paint:{'line-color':'#20baff','line-width':5}});
 map.addSource('nav-route',{type:'geojson',data:{type:'Feature',geometry:{type:'LineString',coordinates:[]}}});
 map.addLayer({id:'nav-route-line',type:'line',source:'nav-route',paint:{'line-color':'#25dcff','line-width':7}});
 carMarker=new maplibregl.Marker({element:car,anchor:'center',rotationAlignment:'map'}).setLngLat(pos).addTo(map);
});
window.updateVehicle=function(lat,lon,head,speed,limit){
 if(!map||!isFinite(lat)||!isFinite(lon))return;
 pos=[lon,lat];bearing=head||0;
 if(carMarker)carMarker.setLngLat(pos).setRotation(bearing);
 trail.push(pos);if(trail.length>1800)trail.shift();
 var src=map.getSource('gps-trail');if(src)src.setData({type:'Feature',geometry:{type:'LineString',coordinates:trail}});
 map.easeTo({center:pos,bearing:bearing,pitch:62,zoom:Math.max(15,Math.min(18,16+speed/120)),duration:850});
};
window.findDestination=async function(q){
 try{
  var g=await fetch('https://nominatim.openstreetmap.org/search?format=json&limit=1&q='+encodeURIComponent(q),{headers:{'Accept-Language':'tr'}});
  var a=await g.json();if(!a.length){alert('Konum bulunamadı');return;}
  var end=[Number(a[0].lon),Number(a[0].lat)];
  var r=await fetch('https://router.project-osrm.org/route/v1/driving/'+pos.join(',')+';'+end.join(',')+'?overview=full&geometries=geojson');
  var d=await r.json();if(!d.routes||!d.routes.length){alert('Rota oluşturulamadı');return;}
  map.getSource('nav-route').setData({type:'Feature',geometry:d.routes[0].geometry});
  var b=new maplibregl.LngLatBounds();d.routes[0].geometry.coordinates.forEach(function(p){b.extend(p);});
  map.fitBounds(b,{padding:{top:190,bottom:100,left:45,right:45},duration:900});
 }catch(e){alert('Harita veya rota bağlantısı kurulamadı');}
};
</script></body></html>
""".trimIndent()
