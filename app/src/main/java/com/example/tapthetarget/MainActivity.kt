package com.example.tapthetarget

import android.content.SharedPreferences
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class MainActivity : AppCompatActivity() {

    // ==== data permainan ====
    private var skor = 0
    private var jumlahKlik = 0
    private val poinPerKlik = 20
    private val durasiPermainan = 30_000L
    private var sisaWaktuDetik = 30
    private var timer: CountDownTimer? = null
    private var sedangDijeda = false
    private var selesai = false
    private val acak = Random()

    // ==== kesulitan: target mengecil tiap 5 klik ====
    private val ukuranAwalDp = 72
    private val ukuranMinDp = 40
    private var ukuranTargetDp = ukuranAwalDp

    // ==== target pindah sendiri bila tidak ditekan ====
    private val handler = Handler(Looper.getMainLooper())
    private val durasiTargetMs = 1_000L
    private val targetKedaluwarsa = Runnable {
        pindahkanTarget()
        jadwalkanTarget()
    }

    // ==== latar acak ====
    private val daftarLatar = intArrayOf(
        R.drawable.bg_game, R.drawable.bg_game_2, R.drawable.bg_game_3
    )
    private var indeksLatar = 0

    // ==== skor tertinggi & suara ====
    private lateinit var prefs: SharedPreferences
    private var toneGen: ToneGenerator? = null

    // ==== view ====
    private lateinit var frameGame: FrameLayout
    private lateinit var imgBackground: ImageView
    private lateinit var imgTarget: ImageView
    private lateinit var tvSkor: TextView
    private lateinit var tvWaktu: TextView
    private lateinit var btnPause: Button
    private lateinit var overlayPause: LinearLayout
    private lateinit var overlayGameOver: LinearLayout
    private lateinit var tvSkorJeda: TextView
    private lateinit var tvSkorAkhir: TextView
    private lateinit var tvRekor: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        frameGame = findViewById(R.id.frameGame)
        imgBackground = findViewById(R.id.imgBackground)
        imgTarget = findViewById(R.id.imgTarget)
        tvSkor = findViewById(R.id.tvSkor)
        tvWaktu = findViewById(R.id.tvWaktu)
        btnPause = findViewById(R.id.btnPause)
        overlayPause = findViewById(R.id.overlayPause)
        overlayGameOver = findViewById(R.id.overlayGameOver)
        tvSkorJeda = findViewById(R.id.tvSkorJeda)
        tvSkorAkhir = findViewById(R.id.tvSkorAkhir)
        tvRekor = findViewById(R.id.tvRekor)

        prefs = getSharedPreferences("tap_prefs", MODE_PRIVATE)
        toneGen = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        } catch (e: RuntimeException) {
            null
        }

        imgTarget.setOnClickListener { targetDitekan() }
        btnPause.setOnClickListener { jedaPermainan() }
        findViewById<Button>(R.id.btnLanjutkan).setOnClickListener { lanjutkanPermainan() }
        findViewById<Button>(R.id.btnUlangi).setOnClickListener { mulaiPermainan() }
        findViewById<Button>(R.id.btnMainLagi).setOnClickListener { mulaiPermainan() }

        if (savedInstanceState == null) {
            mulaiPermainan()
        } else {
            pulihkanPermainan(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("skor", skor)
        outState.putInt("jumlahKlik", jumlahKlik)
        outState.putInt("sisaWaktu", sisaWaktuDetik)
        outState.putBoolean("dijeda", sedangDijeda)
        outState.putBoolean("selesai", selesai)
        outState.putInt("ukuranTarget", ukuranTargetDp)
        outState.putInt("indeksLatar", indeksLatar)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        handler.removeCallbacks(targetKedaluwarsa)
        toneGen?.release()
    }

    // ================= util =================
    private fun dp(nilai: Int) = (nilai * resources.displayMetrics.density).toInt()

    private fun terapkanUkuranTarget() {
        val px = dp(ukuranTargetDp)
        imgTarget.layoutParams = imgTarget.layoutParams.apply {
            width = px
            height = px
        }
    }

    private fun jadwalkanTarget() {
        handler.removeCallbacks(targetKedaluwarsa)
        handler.postDelayed(targetKedaluwarsa, durasiTargetMs)
    }

    private fun hentikanJadwalTarget() {
        handler.removeCallbacks(targetKedaluwarsa)
    }

    // ================= mulai / ulang =================
    private fun mulaiPermainan() {
        skor = 0
        jumlahKlik = 0
        sisaWaktuDetik = 30
        sedangDijeda = false
        selesai = false
        ukuranTargetDp = ukuranAwalDp
        terapkanUkuranTarget()

        // latar acak tiap permainan baru
        indeksLatar = acak.nextInt(daftarLatar.size)
        imgBackground.setImageResource(daftarLatar[indeksLatar])

        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)

        overlayPause.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        overlayGameOver.alpha = 1f
        imgTarget.visibility = View.VISIBLE
        imgTarget.isEnabled = true
        imgTarget.scaleX = 1f
        imgTarget.scaleY = 1f
        btnPause.isEnabled = true

        frameGame.post { pindahkanTarget() }
        jalankanTimer(durasiPermainan)
        jadwalkanTarget()
    }

    // ================= pulihkan setelah rotasi =================
    private fun pulihkanPermainan(b: Bundle) {
        skor = b.getInt("skor")
        jumlahKlik = b.getInt("jumlahKlik")
        sisaWaktuDetik = b.getInt("sisaWaktu")
        sedangDijeda = b.getBoolean("dijeda")
        selesai = b.getBoolean("selesai")
        ukuranTargetDp = b.getInt("ukuranTarget", ukuranAwalDp)
        indeksLatar = b.getInt("indeksLatar")

        imgBackground.setImageResource(daftarLatar[indeksLatar])
        terapkanUkuranTarget()
        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
        overlayPause.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        frameGame.post { pindahkanTarget() }

        when {
            selesai -> gameOver()
            sedangDijeda -> {
                imgTarget.isEnabled = false
                tvSkorJeda.text = skor.toString()
                overlayPause.visibility = View.VISIBLE
            }
            else -> {
                jalankanTimer(sisaWaktuDetik * 1000L)
                jadwalkanTarget()
            }
        }
    }

    // ================= posisi acak target =================
    private fun pindahkanTarget() {
        val lebarArea = frameGame.width
        val tinggiArea = frameGame.height
        if (lebarArea == 0 || tinggiArea == 0) return

        val ukuranPx = dp(ukuranTargetDp)
        val jarakAman = 24
        val maxX = lebarArea - ukuranPx - (jarakAman * 2)
        val maxY = tinggiArea - ukuranPx - (jarakAman * 2)
        if (maxX <= 0 || maxY <= 0) return

        imgTarget.x = (jarakAman + acak.nextInt(maxX)).toFloat()
        imgTarget.y = (jarakAman + acak.nextInt(maxY)).toFloat()
    }

    // ================= klik target =================
    private fun targetDitekan() {
        if (sedangDijeda || selesai) return

        skor += poinPerKlik
        jumlahKlik++
        tvSkor.text = getString(R.string.label_skor, skor)

        // efek suara
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)

        // tiap 5 klik target mengecil 8dp, minimal 40dp
        if (jumlahKlik % 5 == 0 && ukuranTargetDp > ukuranMinDp) {
            ukuranTargetDp = maxOf(ukuranMinDp, ukuranTargetDp - 8)
            terapkanUkuranTarget()
        }

        imgTarget.animate()
            .scaleX(0.6f).scaleY(0.6f).setDuration(70)
            .withEndAction {
                imgTarget.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
            }
            .start()

        tampilkanPoinMelayang()
        pindahkanTarget()
        jadwalkanTarget() // hitung ulang 1 detik dari klik terakhir
    }

    private fun tampilkanPoinMelayang() {
        val label = TextView(this).apply {
            text = "+$poinPerKlik"
            setTextColor(0xFF86EFAC.toInt())
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        frameGame.addView(label)
        label.x = imgTarget.x + 20f
        label.y = imgTarget.y - 30f

        label.animate()
            .alpha(0f)
            .translationYBy(-60f)
            .setDuration(600)
            .withEndAction { frameGame.removeView(label) }
            .start()
    }

    // ================= timer =================
    private fun jalankanTimer(durasi: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durasi, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetik = (millisUntilFinished / 1000).toInt()
                tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            }

            override fun onFinish() {
                sisaWaktuDetik = 0
                tvWaktu.text = getString(R.string.label_waktu, 0)
                gameOver()
            }
        }.start()
    }

    // ================= jeda & lanjut =================
    private fun jedaPermainan() {
        if (sedangDijeda || selesai) return
        sedangDijeda = true

        timer?.cancel()
        hentikanJadwalTarget()
        imgTarget.isEnabled = false

        tvSkorJeda.text = skor.toString()
        overlayPause.alpha = 0f
        overlayPause.visibility = View.VISIBLE
        overlayPause.animate().alpha(1f).setDuration(200).start()
    }

    private fun lanjutkanPermainan() {
        if (!sedangDijeda) return
        sedangDijeda = false

        overlayPause.visibility = View.GONE
        imgTarget.isEnabled = true
        jalankanTimer(sisaWaktuDetik * 1000L)
        jadwalkanTarget()
    }

    // ================= game over =================
    private fun gameOver() {
        timer?.cancel()
        hentikanJadwalTarget()
        sedangDijeda = false
        selesai = true

        imgTarget.isEnabled = false
        imgTarget.visibility = View.GONE
        btnPause.isEnabled = false
        overlayPause.visibility = View.GONE

        tvSkorAkhir.text = skor.toString()

        // skor tertinggi (SharedPreferences)
        val rekorLama = prefs.getInt("skor_tertinggi", 0)
        if (skor > rekorLama) {
            prefs.edit().putInt("skor_tertinggi", skor).apply()
            tvRekor.text = getString(R.string.rekor_baru)
        } else {
            tvRekor.text = getString(R.string.label_rekor, rekorLama)
        }

        overlayGameOver.alpha = 0f
        overlayGameOver.visibility = View.VISIBLE
        overlayGameOver.animate().alpha(1f).setDuration(350).start()
    }
}