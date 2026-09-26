package com.aldanmaz.drivedashboard.ui.screen.vehicle

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aldanmaz.drivedashboard.data.vehicle.VehicleCatalog
import com.aldanmaz.drivedashboard.data.vehicle.VehicleCatalogItem
import com.aldanmaz.drivedashboard.data.vehicle.VehicleImageProcessor
import com.aldanmaz.drivedashboard.data.vehicle.VehicleType
import com.aldanmaz.drivedashboard.data.vehicle.VehicleVisualSource

private val VehicleScreenBackground = Color(0xFF071018)
private val VehicleCardBackground = Color(0xFF101D27)
private val VehicleCardSelectedBackground = Color(0xFF102B36)
private val VehicleBorder = Color(0xFF29404E)
private val VehicleSelectedBorder = Color(0xFF00D8FF)
private val VehiclePrimaryText = Color(0xFFF2F7FA)
private val VehicleSecondaryText = Color(0xFF91A6B3)
private val VehicleAccent = Color(0xFF00D8FF)

/**
 * ALDANMAZ Drive - Araç Deposu / Araç Seçim Ekranı
 *
 * Galeriden seçilen otomobil/karavan fotoğrafı doğrudan kaydedilmez.
 * Önce VehicleImageProcessor ile arka plan ayrılır ve şeffaf PNG oluşturulur.
 */
@Composable
fun VehicleSelectionScreen(
    selectedVehicleId: String,
    customCarImageUri: String?,
    customCaravanImageUri: String?,
    onVehicleSelected: (String) -> Unit,
    onCustomImageChanged: (VehicleType, String?) -> Unit,
    onBack: () -> Unit = {}
) {
    val vehicles =
        VehicleCatalog.all()

    val context = LocalContext.current

    var pendingVehicleType by remember {
        mutableStateOf<VehicleType?>(null)
    }

    var processingVehicleType by remember {
        mutableStateOf<VehicleType?>(null)
    }

    val photoPicker =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            val vehicleType =
                pendingVehicleType

            pendingVehicleType = null

            if (
                uri != null &&
                vehicleType != null
            ) {
                processingVehicleType =
                    vehicleType

                VehicleImageProcessor.processVehicleImage(
                    context = context,
                    sourceUri = uri,
                    vehicleType = vehicleType,
                    onSuccess = { processedUri ->
                        processingVehicleType = null

                        onCustomImageChanged(
                            vehicleType,
                            processedUri.toString()
                        )

                        Toast.makeText(
                            context,
                            "Araç arka plandan ayrıldı.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onError = { error ->
                        processingVehicleType = null

                        Toast.makeText(
                            context,
                            error.message
                                ?: "Araç görseli işlenemedi.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            }
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VehicleScreenBackground
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 20.dp,
                        vertical = 16.dp
                    )
        ) {
            VehicleSelectionHeader(
                onBack = onBack
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "ARAÇ DEPOSU",
                color = VehiclePrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Gösterge ekranında kullanılacak aracı seç.",
                color = VehicleSecondaryText,
                fontSize = 13.sp
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            CustomVehicleImageRow(
                title = "OTOMOBİL GÖRSELİ",
                hasCustomImage =
                    !customCarImageUri.isNullOrBlank(),
                isProcessing =
                    processingVehicleType ==
                            VehicleType.CAR,
                onPick = {
                    if (processingVehicleType == null) {
                        pendingVehicleType =
                            VehicleType.CAR

                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts
                                    .PickVisualMedia
                                    .ImageOnly
                            )
                        )
                    }
                },
                onRemove = {
                    if (processingVehicleType == null) {
                        onCustomImageChanged(
                            VehicleType.CAR,
                            null
                        )
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            CustomVehicleImageRow(
                title = "KARAVAN GÖRSELİ",
                hasCustomImage =
                    !customCaravanImageUri.isNullOrBlank(),
                isProcessing =
                    processingVehicleType ==
                            VehicleType.CARAVAN,
                onPick = {
                    if (processingVehicleType == null) {
                        pendingVehicleType =
                            VehicleType.CARAVAN

                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts
                                    .PickVisualMedia
                                    .ImageOnly
                            )
                        )
                    }
                },
                onRemove = {
                    if (processingVehicleType == null) {
                        onCustomImageChanged(
                            VehicleType.CARAVAN,
                            null
                        )
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = vehicles,
                    key = { vehicle ->
                        vehicle.id
                    }
                ) { vehicle ->

                    VehicleSelectionCard(
                        vehicle = vehicle,
                        isSelected =
                            vehicle.id == selectedVehicleId,
                        onClick = {
                            onVehicleSelected(vehicle.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomVehicleImageRow(
    title: String,
    hasCustomImage: Boolean,
    isProcessing: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = VehicleCardBackground
            ),
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (hasCustomImage) {
                        VehicleSelectedBorder
                            .copy(alpha = 0.75f)
                    } else {
                        VehicleBorder
                    }
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = VehiclePrimaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(2.dp)
                )

                Text(
                    text =
                        when {
                            isProcessing ->
                                "Araç ayrıştırılıyor..."

                            hasCustomImage ->
                                "Şeffaf araç görseli hazır"

                            else ->
                                "Varsayılan araç görseli kullanılıyor"
                        },
                    color =
                        if (
                            hasCustomImage ||
                            isProcessing
                        ) {
                            VehicleAccent
                        } else {
                            VehicleSecondaryText
                        },
                    fontSize = 11.sp,
                    fontWeight =
                        if (
                            hasCustomImage ||
                            isProcessing
                        ) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        }
                )
            }

            Text(
                text =
                    if (isProcessing) {
                        "İŞLENİYOR"
                    } else {
                        "GALERİDEN SEÇ"
                    },
                modifier =
                    Modifier
                        .clickable(
                            enabled = !isProcessing
                        ) {
                            onPick()
                        }
                        .padding(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        ),
                color =
                    if (isProcessing) {
                        VehicleSecondaryText
                    } else {
                        VehicleAccent
                    },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            if (
                hasCustomImage &&
                !isProcessing
            ) {
                Text(
                    text = "KALDIR",
                    modifier =
                        Modifier
                            .clickable {
                                onRemove()
                            }
                            .padding(
                                horizontal = 8.dp,
                                vertical = 8.dp
                            ),
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VehicleSelectionHeader(
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Card(
            modifier =
                Modifier
                    .clickable {
                        onBack()
                    },
            shape = RoundedCornerShape(10.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        VehicleCardBackground
                ),
            border =
                BorderStroke(
                    width = 1.dp,
                    color = VehicleBorder
                )
        ) {
            Text(
                text = "‹",
                modifier =
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 5.dp
                    ),
                color = VehicleAccent,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(
            modifier = Modifier.width(12.dp)
        )

        Text(
            text = "Araç Seçimi",
            color = VehiclePrimaryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VehicleSelectionCard(
    vehicle: VehicleCatalogItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                },
        shape =
            RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        VehicleCardSelectedBackground
                    } else {
                        VehicleCardBackground
                    }
            ),
        border =
            BorderStroke(
                width =
                    if (isSelected) {
                        1.5.dp
                    } else {
                        1.dp
                    },
                color =
                    if (isSelected) {
                        VehicleSelectedBorder
                    } else {
                        VehicleBorder
                    }
            )
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            VehiclePreview(
                vehicle = vehicle
            )

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = vehicle.displayName,
                    color = VehiclePrimaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(
                    modifier = Modifier.height(3.dp)
                )

                Text(
                    text =
                        "${vehicle.brand} • ${vehicle.model} • ${vehicle.colorName}",
                    color = VehicleSecondaryText,
                    fontSize = 12.sp
                )
            }

            if (isSelected) {
                Text(
                    text = "SEÇİLİ",
                    color = VehicleAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VehiclePreview(
    vehicle: VehicleCatalogItem
) {
    val context =
        LocalContext.current

    val drawableResId =
        remember(
            vehicle.id,
            vehicle.drawableName
        ) {
            vehicle.drawableName
                ?.let { drawableName ->
                    context.resources.getIdentifier(
                        drawableName,
                        "drawable",
                        context.packageName
                    )
                }
                ?: 0
        }

    Box(
        modifier =
            Modifier
                .width(112.dp)
                .height(64.dp),
        contentAlignment =
            Alignment.Center
    ) {
        if (
            vehicle.visualSource ==
            VehicleVisualSource.DRAWABLE_RESOURCE &&
            drawableResId != 0
        ) {
            Image(
                painter =
                    painterResource(
                        id = drawableResId
                    ),
                contentDescription =
                    vehicle.displayName,
                contentScale =
                    ContentScale.Fit,
                modifier =
                    Modifier.fillMaxSize()
            )
        } else {
            VehicleTypeBadge(
                type = vehicle.type
            )
        }
    }
}

@Composable
private fun VehicleTypeBadge(
    type: VehicleType
) {
    Box(
        modifier =
            Modifier
                .width(78.dp)
                .height(46.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Card(
            shape =
                RoundedCornerShape(10.dp),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        Color(0xFF09151D)
                ),
            border =
                BorderStroke(
                    width = 1.dp,
                    color =
                        VehicleAccent.copy(
                            alpha = 0.55f
                        )
                )
        ) {
            Text(
                text =
                    when (type) {
                        VehicleType.CAR ->
                            "OTOMOBİL"

                        VehicleType.CARAVAN ->
                            "KARAVAN"
                    },
                modifier =
                    Modifier.padding(
                        horizontal = 9.dp,
                        vertical = 10.dp
                    ),
                color = VehicleAccent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
