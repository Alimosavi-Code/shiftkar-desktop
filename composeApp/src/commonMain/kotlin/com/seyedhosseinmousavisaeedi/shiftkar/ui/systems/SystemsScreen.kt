package com.seyedhosseinmousavisaeedi.shiftkar.ui.systems

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seyedhosseinmousavisaeedi.shiftkar.platform.openExternalUrl
import com.seyedhosseinmousavisaeedi.shiftkar.ui.SkStrings
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.SkIcons
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.clickableNoRipple
import com.seyedhosseinmousavisaeedi.shiftkar.ui.components.glass
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.IRANSansX
import com.seyedhosseinmousavisaeedi.shiftkar.ui.theme.LocalSk

private data class SystemItem(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val href: String
)

private val SYSTEMS = listOf(
    SystemItem(SkIcons.Clinic, SkStrings.sys_clinic, SkStrings.sys_clinic_desc, "https://nobat.piho.ir/"),
    SystemItem(SkIcons.Card, SkStrings.sys_petrocard, SkStrings.sys_petrocard_desc, "https://www.posc.ir/fa/petrocard"),
    SystemItem(SkIcons.Smartphone, SkStrings.sys_petroapp, SkStrings.sys_petroapp_desc, "https://petrocard.sabinarya.com/app"),
    SystemItem(SkIcons.Shield, SkStrings.sys_alborz, SkStrings.sys_alborz_desc, "https://myalborz.yarhis.ir/simple"),
    SystemItem(SkIcons.File, SkStrings.sys_nioc_file, SkStrings.sys_nioc_file_desc, "https://portal.nioc.ir"),
    SystemItem(SkIcons.Briefcase, SkStrings.sys_bipc, SkStrings.sys_bipc_desc, "https://bipc.ir/fa/"),
    SystemItem(SkIcons.Clipboard, SkStrings.sys_flight_request, SkStrings.sys_flight_request_desc, "https://travelrequest.bipc.ir/Guest/LoginPage.aspx"),
    SystemItem(SkIcons.Plane, SkStrings.sys_flight_schedule, SkStrings.sys_flight_schedule_desc, "https://www.posc.ir/fa/publicservices/mosaferat"),
    SystemItem(SkIcons.Food, SkStrings.sys_food, SkStrings.sys_food_desc, "https://pooneh.bipc.ir/"),
    SystemItem(SkIcons.Chart, SkStrings.sys_saham, SkStrings.sys_saham_desc, "https://sahamfasl.bipc.ir/"),
    SystemItem(SkIcons.Globe, SkStrings.sys_web, SkStrings.sys_web_desc, "https://alimosavi-code.github.io"),
    SystemItem(SkIcons.Download, SkStrings.sys_mobile, SkStrings.sys_mobile_desc, "https://alimosavi-code.github.io/download")
)

@Composable
fun SystemsScreen() {
    val sk = LocalSk.current

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        val cols = when {
            maxWidth >= 940.dp -> 4
            maxWidth >= 620.dp -> 3
            else -> 2
        }
        val rowsCount = (SYSTEMS.size + cols - 1) / cols
        val headerReserved = 82.dp
        val navClearance = 96.dp
        val vGap = 14.dp
        val hGap = 14.dp
        val availGridH = (maxHeight - headerReserved - navClearance - (vGap * (rowsCount - 1))).coerceAtLeast(360.dp)
        val cardH = (availGridH / rowsCount).coerceIn(128.dp, 220.dp)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = navClearance)
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        SkStrings.systems_title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = IRANSansX,
                        color = sk.text
                    )
                    Text(
                        SkStrings.systems_subtitle,
                        Modifier.padding(top = 3.dp),
                        fontSize = 13.sp,
                        fontFamily = IRANSansX,
                        color = sk.textMuted
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .background(sk.primarySoft)
                        .border(1.dp, sk.primary.copy(alpha = 0.4f), RoundedCornerShape(100))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "۱۲ سامانه فعال",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = IRANSansX,
                        color = sk.primary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(vGap)) {
                SYSTEMS.chunked(cols).forEach { rowItems ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(hGap)
                    ) {
                        rowItems.forEach { item ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .height(cardH)
                                    .glass(RoundedCornerShape(22.dp))
                                    .clickableNoRipple { openExternalUrl(item.href) }
                                    .padding(18.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(sk.primarySoft)
                                            .border(1.dp, sk.primary.copy(alpha = 0.35f), RoundedCornerShape(15.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(item.icon, item.title, Modifier.size(24.dp), tint = sk.primary)
                                    }
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(sk.surfaceStrong)
                                            .border(1.dp, sk.borderSoft, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(SkIcons.External, null, Modifier.size(15.dp), tint = sk.primary)
                                    }
                                }

                                Column {
                                    Text(
                                        item.title,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = IRANSansX,
                                        color = sk.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.desc,
                                        Modifier.padding(top = 4.dp),
                                        fontSize = 12.5.sp,
                                        lineHeight = 20.sp,
                                        fontFamily = IRANSansX,
                                        color = sk.textMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        repeat(cols - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
