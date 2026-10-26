package com.seyedhosseinmousavisaeedi.shiftkar.ui.systems

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text(SkStrings.systems_title, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = IRANSansX, color = sk.text)
        Text(
            SkStrings.systems_subtitle,
            Modifier.padding(top = 4.dp),
            fontSize = 13.sp, fontFamily = IRANSansX, color = sk.textMuted
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 108.dp)
        ) {
            itemsIndexed(SYSTEMS) { _, item ->
                Column(
                    Modifier
                        .glass(RoundedCornerShape(24.dp))
                        .clickableNoRipple {
                            openExternalUrl(item.href)
                        }
                        .padding(16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(sk.primarySoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, item.title, Modifier.size(21.dp), tint = sk.primary)
                        }
                        Icon(SkIcons.External, null, Modifier.size(15.dp), tint = sk.textFaint)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = IRANSansX, color = sk.text)
                    Text(
                        item.desc,
                        Modifier.padding(top = 2.dp),
                        fontSize = 11.5.sp, lineHeight = 20.sp, fontFamily = IRANSansX, color = sk.textMuted
                    )
                }
            }
        }
    }
}
