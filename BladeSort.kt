package com.example.balancer

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

const val N = 60
const val STEP = 360.0 / N

/** خروجی: [اندازه نابالانسی، زاویه نقطه سنگین از جایگاه ۱ در جهت شماره‌گذاری] */
fun bladeVector(m: DoubleArray, order: IntArray): DoubleArray {
    var x = 0.0
    var y = 0.0
    for (s in 0 until N) {
        val a = Math.toRadians(s * STEP)
        x += m[order[s]] * sin(a)
        y += m[order[s]] * cos(a)
    }
    return doubleArrayOf(hypot(x, y), (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0)
}

/** شبیه‌سازی تبرید روی جابه‌جایی جفت جایگاه‌ها برای کمینه کردن نابالانسی باقیمانده */
fun optimizeBlades(m: DoubleArray, start: IntArray, iterations: Int = 400_000): IntArray {
    val range = m.max() - m.min()
    if (range == 0.0) return start
    val sx = DoubleArray(N) { sin(Math.toRadians(it * STEP)) }
    val cy = DoubleArray(N) { cos(Math.toRadians(it * STEP)) }
    val ord = start.copyOf()
    var x = 0.0
    var y = 0.0
    for (s in 0 until N) { x += m[ord[s]] * sx[s]; y += m[ord[s]] * cy[s] }
    var e = x * x + y * y
    var best = ord.copyOf()
    var bestE = e
    val t0 = (range / 2).pow(2)
    val alpha = (1e-9).pow(1.0 / iterations)
    var t = t0
    repeat(iterations) {
        val i = Random.nextInt(N)
        val j = Random.nextInt(N)
        if (i != j) {
            val dm = m[ord[j]] - m[ord[i]]
            val nx = x + dm * (sx[i] - sx[j])
            val ny = y + dm * (cy[i] - cy[j])
            val ne = nx * nx + ny * ny
            val d = ne - e
            if (d < 0 || Random.nextDouble() < exp(-d / t)) {
                val tmp = ord[i]; ord[i] = ord[j]; ord[j] = tmp
                x = nx; y = ny; e = ne
                if (e < bestE) { bestE = e; best = ord.copyOf() }
            }
        }
        t *= alpha
    }
    return best
}
