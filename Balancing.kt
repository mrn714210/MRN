package com.example.balancer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** عدد مختلط برای کار با دامنه و فاز */
data class Cx(val re: Double, val im: Double) {
    operator fun plus(o: Cx) = Cx(re + o.re, im + o.im)
    operator fun minus(o: Cx) = Cx(re - o.re, im - o.im)
    operator fun times(o: Cx) = Cx(re * o.re - im * o.im, re * o.im + im * o.re)
    operator fun times(k: Double) = Cx(re * k, im * k)
    operator fun div(o: Cx): Cx {
        val d = o.re * o.re + o.im * o.im
        return Cx((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
    }
    fun conj() = Cx(re, -im)
    val abs: Double get() = hypot(re, im)
    val angleDeg: Double get() = (Math.toDegrees(atan2(im, re)) + 360.0) % 360.0

    companion object {
        val ZERO = Cx(0.0, 0.0)
        fun polar(mag: Double, deg: Double): Cx {
            val r = Math.toRadians(deg)
            return Cx(mag * cos(r), mag * sin(r))
        }
    }
}

/**
 * یک سرعت: v0 = ارتعاش اولیه در هر نقطه اندازه‌گیری،
 * trial[p][m] = ارتعاش نقطه m هنگام نصب وزنه آزمایشی فقط در صفحه p
 */
class SpeedData(val rpm: Double, val v0: List<Cx>, val trial: List<List<Cx>>)

class BalanceInput(
    val radii: List<Double>,        // شعاع صفحات (mm)
    val trialMass: List<Double>,    // وزنه آزمایشی هر صفحه (g)
    val trialAngle: List<Double>,   // زاویه وزنه آزمایشی (درجه)
    val speeds: List<SpeedData>
)

class BalanceResult(
    val mass: List<Double>,         // جرم اصلاحی هر صفحه (g) در شعاع همان صفحه
    val angle: List<Double>,        // زاویه وزنه اصلاحی (درجه)، هم‌قرارداد با زاویه وزنه آزمایشی
    val moment: List<Double>,       // g·mm
    val before: List<List<Double>>, // [سرعت][نقطه] دامنه قبل
    val after: List<List<Double>>   // [سرعت][نقطه] دامنه پیش‌بینی‌شده بعد
)

/**
 * ضریب تأثیر: α = (V_trial − V0) / (ممان وزنه آزمایشی)   [واحد: دامنه بر g·mm]
 * حداقل مربعات روی همه سرعت‌ها و نقاط: min Σ |V0 + A·U|²
 * معادله نرمال: (AᴴA) U = −Aᴴ V0  ، سپس جرم = |U| / شعاع
 */
fun solveBalance(inp: BalanceInput): BalanceResult {
    val p = inp.radii.size
    val m = inp.speeds.first().v0.size
    val trialMoment = List(p) { Cx.polar(inp.trialMass[it] * inp.radii[it], inp.trialAngle[it]) }

    // infl[k][mi][pi]
    val infl = inp.speeds.map { s ->
        List(m) { mi -> List(p) { pi -> (s.trial[pi][mi] - s.v0[mi]) / trialMoment[pi] } }
    }

    val h = Array(p) { Array(p) { Cx.ZERO } }
    val g = Array(p) { Cx.ZERO }
    for (k in inp.speeds.indices) for (mi in 0 until m) {
        val v0 = inp.speeds[k].v0[mi]
        for (i in 0 until p) {
            g[i] = g[i] - infl[k][mi][i].conj() * v0
            for (j in 0 until p) h[i][j] = h[i][j] + infl[k][mi][i].conj() * infl[k][mi][j]
        }
    }
    val u = gauss(h, g)

    val before = inp.speeds.map { s -> s.v0.map { it.abs } }
    val after = inp.speeds.indices.map { k ->
        List(m) { mi ->
            var v = inp.speeds[k].v0[mi]
            for (pi in 0 until p) v = v + infl[k][mi][pi] * u[pi]
            v.abs
        }
    }
    return BalanceResult(
        mass = List(p) { u[it].abs / inp.radii[it] },
        angle = List(p) { u[it].angleDeg },
        moment = List(p) { u[it].abs },
        before = before,
        after = after
    )
}

private fun gauss(a: Array<Array<Cx>>, b: Array<Cx>): Array<Cx> {
    val n = b.size
    val scale = a.maxOf { row -> row.maxOf { it.abs } }
    for (c in 0 until n) {
        var piv = c
        for (r in c + 1 until n) if (a[r][c].abs > a[piv][c].abs) piv = r
        if (a[piv][c].abs <= 1e-9 * scale || scale == 0.0)
            throw IllegalArgumentException("ماتریس ضرایب تأثیر تکین است؛ داده‌ها مستقل یا کافی نیستند")
        val tr = a[c]; a[c] = a[piv]; a[piv] = tr
        val tb = b[c]; b[c] = b[piv]; b[piv] = tb
        for (r in c + 1 until n) {
            val f = a[r][c] / a[c][c]
            for (k in c until n) a[r][k] = a[r][k] - f * a[c][k]
            b[r] = b[r] - f * b[c]
        }
    }
    val x = Array(n) { Cx.ZERO }
    for (r in n - 1 downTo 0) {
        var s = b[r]
        for (k in r + 1 until n) s = s - a[r][k] * x[k]
        x[r] = s / a[r][r]
    }
    return x
}
