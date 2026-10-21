@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.balancer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.Typeface
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale
import kotlin.random.Random

// ---------- ابزارهای کمکی ----------

fun String.fixDigits(): String = trim().map { c ->
    when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        '٫', ',', '،' -> '.'
        else -> c
    }
}.joinToString("")

fun String.num(): Double? = fixDigits().toDoubleOrNull()

fun Double.f(d: Int = 2): String = String.format(Locale.US, "%.${d}f", this)

fun need(x: String, name: String): Double =
    x.num() ?: throw IllegalArgumentException("مقدار «$name» خالی یا نامعتبر است")

// ---------- وضعیت‌ها (بالاتر از تب‌ها نگه داشته می‌شوند تا با تعویض تب پاک نشوند) ----------

class BladeState {
    var text by mutableStateOf("")
    var radius by mutableStateOf("1")
    var ccw by mutableStateOf(false)
    var output by mutableStateOf("")
}

class SpeedState(p: Int) {
    var rpm by mutableStateOf("")
    val v0a = mutableStateListOf<String>().apply { repeat(p) { add("") } }
    val v0p = mutableStateListOf<String>().apply { repeat(p) { add("") } }
    val ta = List(p) { mutableStateListOf<String>().apply { repeat(p) { add("") } } }
    val tp = List(p) { mutableStateListOf<String>().apply { repeat(p) { add("") } } }
}

class BalState(val planes: Int) {
    val radius = mutableStateListOf<String>().apply { repeat(planes) { add("") } }
    val tMass = mutableStateListOf<String>().apply { repeat(planes) { add("") } }
    val tAngle = mutableStateListOf<String>().apply { repeat(planes) { add("") } }
    val newR = mutableStateListOf<String>().apply { repeat(planes) { add("") } }
    val speeds = mutableStateListOf(SpeedState(planes))
    var output by mutableStateOf("")
}

// ---------- محاسبه ----------

fun bladeReport(st: BladeState, optimize: Boolean) {
    st.output = try {
        val xs = st.text.split(Regex("[\\s;،]+")).filter { it.isNotBlank() }
            .map { it.num() ?: throw IllegalArgumentException("مقدار نامعتبر: $it") }
        require(xs.size == N) { "تعداد مقادیر ${xs.size} است؛ باید $N باشد" }
        val r = need(st.radius, "شعاع")
        val m = DoubleArray(N) { xs[it] * r }
        val identity = IntArray(N) { it }
        val v0 = bladeVector(m, identity)
        val order = if (optimize) optimizeBlades(m, identity) else identity
        val v = bladeVector(m, order)
        val dir = if (st.ccw) "چپگرد" else "راستگرد"
        val slot = (Math.round(v[1] / STEP).toInt() % N) + 1
        buildString {
            appendLine("جهت شماره‌گذاری جایگاه‌ها: $dir (جایگاه‌ها ${STEP.f(0)}° فاصله دارند)")
            if (optimize) appendLine("نابالانسی ترتیب ورود: ${v0[0].f(3)} g·mm")
            appendLine(if (optimize) "نابالانسی پس از بهینه‌سازی: ${v[0].f(3)} g·mm" else "نابالانسی باقیمانده: ${v[0].f(3)} g·mm")
            appendLine("معادل جرم در شعاع ${r.f(2)} mm: ${(v[0] / r).f(4)} g")
            appendLine("نقطه سنگین: ${v[1].f(1)}° از جایگاه ۱ در جهت $dir (نزدیک‌ترین جایگاه: $slot)")
            appendLine("جهت اصلاح (روبه‌رو): ${((v[1] + 180) % 360).f(1)}°")
            appendLine()
            appendLine("ترتیب نصب (جایگاه ← شماره پره):")
            for (s in 0 until N) appendLine("جایگاه ${s + 1} ← پره ${order[s] + 1}  (${xs[order[s]].f(2)})")
        }
    } catch (e: Exception) {
        "خطا: ${e.message}"
    }
}

fun balanceReport(st: BalState): String = try {
    val p = st.planes
    val radii = st.radius.map { need(it, "شعاع صفحه") }
    val tMass = st.tMass.map { need(it, "وزنه آزمایشی") }
    val tAngle = st.tAngle.map { need(it, "زاویه وزنه آزمایشی") }
    require(radii.all { it > 0 } && tMass.all { it > 0 }) { "شعاع و وزنه آزمایشی باید مثبت باشند" }
    val speeds = st.speeds.mapIndexed { k, s ->
        SpeedData(
            rpm = need(s.rpm, "سرعت ${k + 1}"),
            v0 = List(p) { Cx.polar(need(s.v0a[it], "دامنه اولیه"), need(s.v0p[it], "فاز اولیه")) },
            trial = List(p) { pi ->
                List(p) { mi -> Cx.polar(need(s.ta[pi][mi], "دامنه با وزنه آزمایشی"), need(s.tp[pi][mi], "فاز با وزنه آزمایشی")) }
            }
        )
    }
    require(speeds.isNotEmpty()) { "حداقل یک سرعت لازم است" }
    val res = solveBalance(BalanceInput(radii, tMass, tAngle, speeds))
    buildString {
        appendLine("وزنه اصلاحی (حداقل مربعات روی ${speeds.size} سرعت):")
        for (i in 0 until p) {
            appendLine("صفحه ${i + 1}: ${res.mass[i].f(2)} g در زاویه ${res.angle[i].f(1)}°  (ممان ${res.moment[i].f(1)} g·mm، شعاع ${radii[i].f(1)} mm)")
            if (st.newR[i].isNotBlank()) {
                val nr = need(st.newR[i], "شعاع نصب وزنه اصلاحی")
                require(nr > 0) { "شعاع نصب باید مثبت باشد" }
                appendLine("   اگر وزنه در شعاع ${nr.f(1)} mm نصب شود: ${(res.moment[i] / nr).f(2)} g در همان زاویه")
            }
        }
        appendLine()
        appendLine("پیش‌بینی ارتعاش (قبل ← بعد):")
        for (k in speeds.indices) for (mi in 0 until p) {
            val b = res.before[k][mi]
            val a = res.after[k][mi]
            val red = if (b > 0) (1 - a / b) * 100 else 0.0
            appendLine("${speeds[k].rpm.f(0)} rpm، نقطه ${mi + 1}: ${b.f(3)} ← ${a.f(3)}  (${red.f(0)}٪ کاهش)")
        }
        appendLine()
        append("زاویه خروجی با همان قرارداد زاویه وزنه آزمایشی است.")
    }
} catch (e: Exception) {
    "خطا: ${e.message}"
}

// ---------- رابط کاربری ----------

class MainActivity : ComponentActivity() {
    private val blade = BladeState()
    private val one = BalState(1)
    private val two = BalState(2)
    private val weights = WeightState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // فونت نستعلیق را از app/src/main/assets/fonts/nastaliq.ttf می‌خواند؛ اگر نبود فونت پیش‌فرض سیستم
        val nastaliq: FontFamily = try {
            FontFamily(Typeface.createFromAsset(assets, "fonts/nastaliq.ttf"))
        } catch (e: Exception) {
            FontFamily.Default
        }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    var splash by remember { mutableStateOf(true) }
                    Crossfade(targetState = splash, animationSpec = tween(700), label = "splash") { showing ->
                        if (showing) SplashScreen(nastaliq) { splash = false }
                        else App(blade, one, two, weights)
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(font: FontFamily, onDone: () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(1100))
        delay(3900) // مجموع نمایش ≈ ۵ ثانیه (۱.۱ ثانیه ظاهر شدن + ۳.۹ ثانیه ماندن)
        onDone()
    }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2A33), Color(0xFF1F6F7C))))
            .clickable { onDone() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = a.value
                scaleX = 0.85f + 0.15f * a.value
                scaleY = 0.85f + 0.15f * a.value
            }
        ) {
            Text(
                "Mrn",
                style = TextStyle(fontFamily = FontFamily.Cursive, fontSize = 96.sp, color = Color.White, letterSpacing = 4.sp)
            )
        }
    }
}

/** تب‌های حلقه‌ای سه‌بعدی: تب انتخاب‌شده جلو و پررنگ؛ بقیه کوچک، تار و کمرنگ پشت حلقه */
@Composable
fun TabCarousel(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val n = titles.size
    val rot = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(selected) {
        // نزدیک‌ترین معادل شاخص انتخاب‌شده تا حلقه کوتاه‌ترین مسیر را بچرخد
        val k = Math.round((rot.value - selected) / n.toFloat())
        rot.animateTo((selected + n * k).toFloat(), tween(650, easing = FastOutSlowInEasing))
    }
    val density = LocalDensity.current
    val radiusPx = with(density) { 104.dp.toPx() }
    val liftPx = with(density) { 16.dp.toPx() }
    Box(Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
        for (i in 0 until n) {
            val ang = (i - rot.value) * 2f * PI.toFloat() / n
            val z = cos(ang)               // 1 = جلو، -1 = پشت
            val front = (z + 1f) / 2f      // 0..1
            Surface(
                onClick = { onSelect(i) },
                shape = RoundedCornerShape(14.dp),
                color = if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .width(112.dp).height(54.dp)
                    .zIndex(z)
                    .graphicsLayer {
                        cameraDistance = 14f * density.density
                        translationX = radiusPx * sin(ang)
                        translationY = -(1f - front) * liftPx
                        rotationY = -sin(ang) * 40f
                        val sc = 0.62f + 0.38f * front
                        scaleX = sc
                        scaleY = sc
                        alpha = 0.22f + 0.78f * front * front
                    }
                    .blur(((1f - front) * 3f).dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(titles[i], maxLines = 2, textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 16.sp, modifier = Modifier.padding(4.dp))
                }
            }
        }
    }
}

@Composable
fun App(blade: BladeState, one: BalState, two: BalState, weights: WeightState) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("چیدمان پره", "بالانس ۱ صفحه", "بالانس ۲ صفحه", "جمع و تقسیم وزنه")
    Scaffold { pad ->
        Column(Modifier.padding(pad).statusBarsPadding()) {
            TabCarousel(titles, tab) { tab = it }
            when (tab) {
                0 -> BladeScreen(blade)
                1 -> BalanceScreen(one)
                2 -> BalanceScreen(two)
                else -> WeightScreen(weights)
            }
        }
    }
}

@Composable
fun NumField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, maxLines = 1) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun BladeScreen(st: BladeState) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("چیدمان ۶۰ پره و نابالانسی باقیمانده", style = MaterialTheme.typography.titleMedium)
        Text("جهت شماره‌گذاری جایگاه‌ها (با نگاه از همان سمتی که در نقشه مبنا است):")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !st.ccw, onClick = { st.ccw = false }, label = { Text("راستگرد (ساعتگرد)") })
            FilterChip(selected = st.ccw, onClick = { st.ccw = true }, label = { Text("چپگرد (پادساعتگرد)") })
        }
        NumField("شعاع مرکز جرم پره (mm) — برای ممان وارد‌شده 1", st.radius, { st.radius = it }, Modifier.fillMaxWidth())
        OutlinedTextField(
            value = st.text, onValueChange = { st.text = it },
            label = { Text("وزن ۶۰ پره (g) به ترتیب شماره پره، با فاصله یا خط جدید") },
            minLines = 5, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { bladeReport(st, false) }) { Text("محاسبه") }
            Button(onClick = { bladeReport(st, true) }) { Text("بهینه‌سازی") }
            TextButton(onClick = {
                st.text = (1..N).joinToString("\n") { (Random.nextDouble(247.0, 253.0)).f(2) }
            }) { Text("نمونه") }
        }
        if (st.output.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(st.output, Modifier.padding(10.dp)) }
    }
}

@Composable
fun BalanceScreen(st: BalState) {
    val p = st.planes
    val w = Modifier.weight(1f)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (p == 1) "بالانس تک‌صفحه‌ای (ضریب تأثیر + حداقل مربعات)" else "بالانس دوصفحه‌ای (ضریب تأثیر + حداقل مربعات)",
            style = MaterialTheme.typography.titleMedium
        )
        Text("دامنه با هر واحد دلخواه (یکسان)، فاز و زاویه وزنه با یک قرارداد یکسان وارد شوند.")
        for (i in 0 until p) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("صفحه بالانس ${i + 1}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("شعاع (mm)", st.radius[i], { st.radius[i] = it }, w)
                    NumField("وزنه آزمایشی (g)", st.tMass[i], { st.tMass[i] = it }, w)
                    NumField("زاویه (°)", st.tAngle[i], { st.tAngle[i] = it }, w)
                }
                NumField("شعاع نصب وزنه اصلاحی (mm) — اختیاری", st.newR[i], { st.newR[i] = it }, Modifier.fillMaxWidth())
            }
        }
        st.speeds.forEachIndexed { k, s ->
            key(s) { SpeedCard(k, s, p) { st.speeds.remove(s) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { st.speeds.add(SpeedState(p)) }) { Text("افزودن سرعت") }
            Button(onClick = { st.output = balanceReport(st) }) { Text("محاسبه وزنه اصلاحی") }
        }
        if (st.output.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(st.output, Modifier.padding(10.dp)) }
    }
}

@Composable
fun SpeedCard(k: Int, s: SpeedState, p: Int, onRemove: () -> Unit) {
    val w = Modifier.weight(1f)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumField("سرعت ${k + 1} (rpm)", s.rpm, { s.rpm = it }, w)
                TextButton(onClick = onRemove) { Text("حذف") }
            }
            Text("ارتعاش اولیه (بدون وزنه آزمایشی)")
            for (m in 0 until p) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumField("دامنه نقطه ${m + 1}", s.v0a[m], { s.v0a[m] = it }, w)
                NumField("فاز (°)", s.v0p[m], { s.v0p[m] = it }, w)
            }
            for (pi in 0 until p) {
                Text("با وزنه آزمایشی فقط در صفحه ${pi + 1}")
                for (m in 0 until p) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumField("دامنه نقطه ${m + 1}", s.ta[pi][m], { s.ta[pi][m] = it }, w)
                    NumField("فاز (°)", s.tp[pi][m], { s.tp[pi][m] = it }, w)
                }
            }
        }
    }
}

// ---------- جمع و تقسیم وزنه ----------

class WRow {
    var mass by mutableStateOf("")
    var angle by mutableStateOf("")
    var radius by mutableStateOf("")
}

class WeightState {
    val rows = mutableStateListOf(WRow(), WRow())
    var refRadius by mutableStateOf("")
    var sumOut by mutableStateOf("")
    var sMass by mutableStateOf("")
    var sAngle by mutableStateOf("")
    var sR0 by mutableStateOf("")
    var a1 by mutableStateOf("")
    var r1 by mutableStateOf("")
    var a2 by mutableStateOf("")
    var r2 by mutableStateOf("")
    var splitOut by mutableStateOf("")
}

fun sumReport(st: WeightState): String = try {
    require(st.rows.size >= 2) { "حداقل دو وزنه لازم است" }
    val items = st.rows.mapIndexed { i, r ->
        WeightItem(need(r.mass, "جرم ${i + 1}"), need(r.angle, "زاویه ${i + 1}"), need(r.radius, "شعاع ${i + 1}"))
    }
    require(items.all { it.radius > 0 }) { "شعاع‌ها باید مثبت باشند" }
    val u = sumWeights(items)
    val ref = if (st.refRadius.isBlank()) items[0].radius else need(st.refRadius, "شعاع مبنا")
    require(ref > 0) { "شعاع مبنا باید مثبت باشد" }
    "برآیند: ${(u.abs / ref).f(3)} g در زاویه ${u.angleDeg.f(1)}° در شعاع ${ref.f(1)} mm\n(ممان برآیند ${u.abs.f(2)} g·mm)"
} catch (e: Exception) {
    "خطا: ${e.message}"
}

fun splitReport(st: WeightState): String = try {
    val m = need(st.sMass, "جرم")
    val ang = need(st.sAngle, "زاویه")
    val r0 = need(st.sR0, "شعاع")
    val a1 = need(st.a1, "زاویه موقعیت ۱")
    val a2 = need(st.a2, "زاویه موقعیت ۲")
    val r1 = if (st.r1.isBlank()) r0 else need(st.r1, "شعاع موقعیت ۱")
    val r2 = if (st.r2.isBlank()) r0 else need(st.r2, "شعاع موقعیت ۲")
    require(m > 0 && r0 > 0 && r1 > 0 && r2 > 0) { "جرم و شعاع‌ها باید مثبت باشند" }
    val (m1, m2) = splitWeight(m, ang, r0, a1, r1, a2, r2)
    fun line(i: Int, mass: Double, a: Double) =
        if (mass >= 0) "موقعیت $i: ${mass.f(3)} g در زاویه ${a.f(1)}°"
        else "موقعیت $i: ${(-mass).f(3)} g باید برداشته شود از زاویه ${a.f(1)}° (یا اضافه شود در ${((a + 180) % 360).f(1)}°)"
    buildString {
        appendLine(line(1, m1, a1))
        appendLine(line(2, m2, a2))
        if (m1 < 0 || m2 < 0) append("\nعلامت منفی یعنی زاویه وزنه اصلی بین دو موقعیت قرار ندارد.")
    }
} catch (e: Exception) {
    "خطا: ${e.message}"
}

@Composable
fun WeightScreen(st: WeightState) {
    val w = Modifier.weight(1f)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("جمع وزنه‌ها (برداری)", style = MaterialTheme.typography.titleMedium)
        st.rows.forEachIndexed { i, r ->
            key(r) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumField("جرم ${i + 1} (g)", r.mass, { r.mass = it }, w)
                    NumField("زاویه (°)", r.angle, { r.angle = it }, w)
                    NumField("شعاع (mm)", r.radius, { r.radius = it }, w)
                    TextButton(onClick = { if (st.rows.size > 2) st.rows.remove(r) }) { Text("حذف") }
                }
            }
        }
        NumField("شعاع مبنای نتیجه (mm) — اختیاری", st.refRadius, { st.refRadius = it }, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { st.rows.add(WRow()) }) { Text("افزودن وزنه") }
            Button(onClick = { st.sumOut = sumReport(st) }) { Text("جمع") }
        }
        if (st.sumOut.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(st.sumOut, Modifier.padding(10.dp)) }

        Text("تقسیم یک وزنه بین دو موقعیت", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("جرم (g)", st.sMass, { st.sMass = it }, w)
            NumField("زاویه (°)", st.sAngle, { st.sAngle = it }, w)
            NumField("شعاع (mm)", st.sR0, { st.sR0 = it }, w)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("زاویه موقعیت ۱ (°)", st.a1, { st.a1 = it }, w)
            NumField("شعاع ۱ (mm) اختیاری", st.r1, { st.r1 = it }, w)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NumField("زاویه موقعیت ۲ (°)", st.a2, { st.a2 = it }, w)
            NumField("شعاع ۲ (mm) اختیاری", st.r2, { st.r2 = it }, w)
        }
        Button(onClick = { st.splitOut = splitReport(st) }) { Text("تقسیم") }
        if (st.splitOut.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Text(st.splitOut, Modifier.padding(10.dp)) }
    }
}
