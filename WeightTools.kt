package com.example.balancer

import kotlin.math.abs
import kotlin.math.sin

class WeightItem(val mass: Double, val angle: Double, val radius: Double)

/** جمع برداری وزنه‌ها؛ نتیجه ممان برآیند (g·mm) به‌صورت عدد مختلط */
fun sumWeights(items: List<WeightItem>): Cx =
    items.fold(Cx.ZERO) { acc, w -> acc + Cx.polar(w.mass * w.radius, w.angle) }

/**
 * تقسیم وزنه (mass در زاویه angle و شعاع r0) به دو موقعیت (a1, r1) و (a2, r2)
 * بر اساس قانون سینوس‌ها روی ممان. جرم منفی یعنی باید برداشته شود (یا در ۱۸۰° مقابل اضافه شود).
 */
fun splitWeight(mass: Double, angle: Double, r0: Double, a1: Double, r1: Double, a2: Double, r2: Double): Pair<Double, Double> {
    val s = sin(Math.toRadians(a2 - a1))
    require(abs(s) > 1e-6) { "دو موقعیت نباید هم‌راستا یا روبه‌رو باشند" }
    val u = mass * r0
    val u1 = u * sin(Math.toRadians(a2 - angle)) / s
    val u2 = u * sin(Math.toRadians(angle - a1)) / s
    return Pair(u1 / r1, u2 / r2)
}
