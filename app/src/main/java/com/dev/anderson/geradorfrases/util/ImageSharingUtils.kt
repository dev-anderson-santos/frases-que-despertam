package com.dev.anderson.geradorfrases.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.dev.anderson.geradorfrases.data.Phrase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.cos
import kotlin.math.sin

enum class BackgroundType {
    FLOWERS_FIELD,
    SUNSET_SKY,
    NATURE_GREEN,
    OCEAN_WAVES,
    MOUNTAIN_VIEW,
    GRADIENT_PURPLE,
    GRADIENT_BLUE,
    SOLID_ELEGANT
}

class ImageSharingUtils(private val context: Context) {

    fun sharePhrase(phrase: Phrase, backgroundType: BackgroundType, pexelsBitmap: Bitmap? = null) {
        try {
            val bitmap = createPhraseImage(phrase, backgroundType, pexelsBitmap)
            val uri = saveBitmapToCache(bitmap)

            if (uri != null) {
                // ✅ CORREÇÃO: Incluir texto junto com a imagem
                shareImageWithText(uri, phrase)
            } else {
                // Fallback para compartilhamento de texto
                shareAsText(phrase)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao criar imagem. Compartilhando como texto.", Toast.LENGTH_SHORT).show()
            shareAsText(phrase)
        }
    }

    private fun createPhraseImage(phrase: Phrase, backgroundType: BackgroundType, pexelsBitmap: Bitmap? = null): Bitmap {
        val width = 1080
        val height = 1920

        // Criar bitmap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Desenhar fundo
        drawBackground(canvas, width, height, backgroundType, pexelsBitmap)

        // Adicionar overlay semi-transparente para melhor legibilidade
        addTextOverlay(canvas, width, height)

        // Desenhar elementos decorativos
        addDecorativeElements(canvas, width, height, backgroundType)

        // Desenhar texto principal
        drawPhraseText(canvas, phrase, width, height)

        // Adicionar marca d'água elegante
        addWatermark(canvas, width, height)

        return bitmap
    }

    private fun drawBackground(canvas: Canvas, width: Int, height: Int, backgroundType: BackgroundType, pexelsBitmap: Bitmap? = null) {
        when (backgroundType) {
            BackgroundType.FLOWERS_FIELD -> {
                // Fundo com gradiente verde suave
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(
                            Color.parseColor("#E8F5E8"),
                            Color.parseColor("#A8D8A8"),
                            Color.parseColor("#4A7C59")
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                drawFlowerPattern(canvas, width, height)
            }

            BackgroundType.SUNSET_SKY -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(
                            Color.parseColor("#FFE4B5"),
                            Color.parseColor("#FFA07A"),
                            Color.parseColor("#FF6347"),
                            Color.parseColor("#4B0082")
                        ),
                        floatArrayOf(0f, 0.3f, 0.7f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                drawClouds(canvas, width, height)
            }

            BackgroundType.NATURE_GREEN -> {
                val paint = Paint().apply {
                    shader = RadialGradient(
                        width/2f, height/2f, Math.max(width, height)/2f,
                        intArrayOf(
                            Color.parseColor("#90EE90"),
                            Color.parseColor("#228B22"),
                            Color.parseColor("#006400")
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                drawLeaves(canvas, width, height)
            }

            BackgroundType.OCEAN_WAVES -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(
                            Color.parseColor("#87CEEB"),
                            Color.parseColor("#4682B4"),
                            Color.parseColor("#191970")
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                drawWaves(canvas, width, height)
            }

            BackgroundType.MOUNTAIN_VIEW -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(
                            Color.parseColor("#FFE4E1"),
                            Color.parseColor("#DDA0DD"),
                            Color.parseColor("#8B4513")
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                drawMountains(canvas, width, height)
            }

            BackgroundType.GRADIENT_PURPLE -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(
                            Color.parseColor("#E6E6FA"),
                            Color.parseColor("#9370DB"),
                            Color.parseColor("#4B0082")
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            BackgroundType.GRADIENT_BLUE -> {
                val paint = Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, height.toFloat(),
                        intArrayOf(
                            Color.parseColor("#E0F6FF"),
                            Color.parseColor("#87CEEB"),
                            Color.parseColor("#4682B4")
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            }

            BackgroundType.SOLID_ELEGANT -> {
                canvas.drawColor(Color.parseColor("#2C3E50"))
            }
        }
    }

    private fun addTextOverlay(canvas: Canvas, width: Int, height: Int) {
        // Adicionar overlay semi-transparente no centro para melhor legibilidade
        val overlayPaint = Paint().apply {
            color = Color.parseColor("#40000000") // Preto 25% transparente
        }

        val overlayRect = RectF(
            width * 0.05f,
            height * 0.25f,
            width * 0.95f,
            height * 0.75f
        )

        canvas.drawRoundRect(overlayRect, 30f, 30f, overlayPaint)
    }

    private fun addDecorativeElements(canvas: Canvas, width: Int, height: Int, backgroundType: BackgroundType) {
        when (backgroundType) {
            BackgroundType.FLOWERS_FIELD -> drawFloralBorder(canvas, width, height)
            BackgroundType.SUNSET_SKY -> drawStars(canvas, width, height)
            BackgroundType.NATURE_GREEN -> drawBranches(canvas, width, height)
            BackgroundType.OCEAN_WAVES -> drawSeashells(canvas, width, height)
            else -> drawElegantBorder(canvas, width, height)
        }
    }

    private fun drawPhraseText(canvas: Canvas, phrase: Phrase, width: Int, height: Int) {
        val centerX = width / 2f
        val margin = width * 0.1f
        val availableWidth = width - (2 * margin)

        // Título decorativo
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 2f, 2f, Color.parseColor("#80000000"))
        }

        // Texto principal da frase
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 1f, 1f, Color.parseColor("#80000000"))
        }

        // Autor/referência
        val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F0F8FF")
            textSize = 32f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 1f, 1f, Color.parseColor("#80000000"))
        }

        // Categoria
        val categoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D3D3D3")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            setShadowLayer(1f, 1f, 1f, Color.parseColor("#80000000"))
        }

        var currentY = height * 0.35f

        // Desenhar título
        canvas.drawText("✨ Frase Inspiradora ✨", centerX, currentY, titlePaint)
        currentY += 80f

        // Desenhar aspas decorativas
        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            textSize = 80f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 2f, 2f, Color.parseColor("#80000000"))
        }

        canvas.drawText("“", centerX - availableWidth/3, currentY, quoteMarkPaint)
        
        // Desenhar texto da frase
        currentY += drawMultilineText(canvas, phrase.text, quotePaint, centerX, currentY + 20, availableWidth * 0.8f)
        
        // Aspas de fechamento
        canvas.drawText("”", centerX + availableWidth/3, currentY - 20, quoteMarkPaint)

        currentY += 60f

        // Linha decorativa
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFD700")
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        }
        canvas.drawLine(centerX - 150, currentY, centerX + 150, currentY, linePaint)

        currentY += 50f

        // Desenhar autor
        val authorText = "— ${phrase.reference} —"
        canvas.drawText(authorText, centerX, currentY, authorPaint)

        currentY += 60f

        // Desenhar categoria
        val categoryText = "❀ ${phrase.category} • ${phrase.subcategory} ❀"
        canvas.drawText(categoryText, centerX, currentY, categoryPaint)
    }

    private fun drawMultilineText(canvas: Canvas, text: String, paint: Paint, x: Float, startY: Float, maxWidth: Float): Float {
        val words = text.split(" ")
        var currentY = startY
        var currentLine = ""
        val lineHeight = paint.textSize + 15f

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val testWidth = paint.measureText(testLine)

            if (testWidth <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, x, currentY, paint)
                    currentY += lineHeight
                }
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, x, currentY, paint)
            currentY += lineHeight
        }

        return currentY - startY
    }

    private fun addWatermark(canvas: Canvas, width: Int, height: Int) {
        val watermarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80FFFFFF")
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("✨ Frases que Despertam ✨", width/2f, height - 80f, watermarkPaint)
    }

    // Métodos para desenhar elementos decorativos
    private fun drawFlowerPattern(canvas: Canvas, width: Int, height: Int) {
        val flowerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FF69B4")
            style = Paint.Style.FILL
        }

        // Desenhar flores pequenas espalhadas
        for (i in 0..20) {
            val x = (Math.random() * width).toFloat()
            val y = (Math.random() * height).toFloat()
            drawSimpleFlower(canvas, x, y, flowerPaint)
        }
    }

    private fun drawSimpleFlower(canvas: Canvas, centerX: Float, centerY: Float, paint: Paint) {
        val petalLength = 15f

        // Desenhar 5 pétalas
        for (i in 0..4) {
            val angle = i * 72 * Math.PI / 180
            val endX = centerX + (petalLength * cos(angle)).toFloat()
            val endY = centerY + (petalLength * sin(angle)).toFloat()
            canvas.drawCircle(endX, endY, 8f, paint)
        }

        // Centro da flor
        paint.color = Color.parseColor("#60FFD700")
        canvas.drawCircle(centerX, centerY, 5f, paint)
    }

    private fun drawClouds(canvas: Canvas, width: Int, height: Int) {
        val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.FILL
        }

        // Desenhar algumas nuvens
        drawCloud(canvas, width * 0.2f, height * 0.2f, cloudPaint)
        drawCloud(canvas, width * 0.7f, height * 0.15f, cloudPaint)
        drawCloud(canvas, width * 0.1f, height * 0.8f, cloudPaint)
        drawCloud(canvas, width * 0.8f, height * 0.85f, cloudPaint)
    }

    private fun drawCloud(canvas: Canvas, centerX: Float, centerY: Float, paint: Paint) {
        // Desenhar nuvem com círculos sobrepostos
        canvas.drawCircle(centerX, centerY, 30f, paint)
        canvas.drawCircle(centerX - 25, centerY + 10, 25f, paint)
        canvas.drawCircle(centerX + 25, centerY + 10, 25f, paint)
        canvas.drawCircle(centerX - 10, centerY - 15, 20f, paint)
        canvas.drawCircle(centerX + 15, centerY - 10, 22f, paint)
    }

    private fun drawLeaves(canvas: Canvas, width: Int, height: Int) {
        val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40228B22")
            style = Paint.Style.FILL
        }

        // Desenhar folhas espalhadas
        for (i in 0..15) {
            val x = (Math.random() * width).toFloat()
            val y = (Math.random() * height).toFloat()
            drawLeaf(canvas, x, y, leafPaint)
        }
    }

    private fun drawLeaf(canvas: Canvas, x: Float, y: Float, paint: Paint) {
        val path = Path()
        path.moveTo(x, y)
        path.quadTo(x - 10, y - 20, x, y - 30)
        path.quadTo(x + 10, y - 20, x, y)
        canvas.drawPath(path, paint)
    }

    private fun drawWaves(canvas: Canvas, width: Int, height: Int) {
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val path = Path()
        for (i in 0..3) {
            val y = height * 0.7f + i * 50
            path.reset()
            path.moveTo(0f, y)

            for (x in 0..width step 50) {
                val waveY = y + 20 * sin(x * 0.01).toFloat()
                path.lineTo(x.toFloat(), waveY)
            }
            canvas.drawPath(path, wavePaint)
        }
    }

    private fun drawMountains(canvas: Canvas, width: Int, height: Int) {
        val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40696969")
            style = Paint.Style.FILL
        }

        val path = Path()
        path.moveTo(0f, height * 0.8f)
        path.lineTo(width * 0.2f, height * 0.5f)
        path.lineTo(width * 0.4f, height * 0.6f)
        path.lineTo(width * 0.6f, height * 0.4f)
        path.lineTo(width * 0.8f, height * 0.7f)
        path.lineTo(width.toFloat(), height * 0.6f)
        path.lineTo(width.toFloat(), height.toFloat())
        path.lineTo(0f, height.toFloat())
        path.close()

        canvas.drawPath(path, mountainPaint)
    }

    private fun drawStars(canvas: Canvas, width: Int, height: Int) {
        val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80FFFF00")
            style = Paint.Style.FILL
        }

        for (i in 0..30) {
            val x = (Math.random() * width).toFloat()
            val y = (Math.random() * height * 0.4).toFloat() // Apenas na parte superior
            canvas.drawCircle(x, y, 2f, starPaint)
        }
    }

    private fun drawFloralBorder(canvas: Canvas, width: Int, height: Int) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60FF69B4")
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        // Bordas florais nos cantos
        val cornerSize = 100f

        // Canto superior esquerdo
        val path1 = Path()
        path1.moveTo(0f, cornerSize)
        path1.quadTo(cornerSize/2, 0f, cornerSize, 0f)
        canvas.drawPath(path1, borderPaint)

        // Canto superior direito
        val path2 = Path()
        path2.moveTo(width - cornerSize, 0f)
        path2.quadTo(width - cornerSize/2, 0f, width.toFloat(), cornerSize)
        canvas.drawPath(path2, borderPaint)

        // Cantos inferiores similares...
    }

    private fun drawBranches(canvas: Canvas, width: Int, height: Int) {
        val branchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40654321")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Desenhar galhos nos cantos
        canvas.drawLine(0f, 0f, width * 0.3f, height * 0.2f, branchPaint)
        canvas.drawLine(width.toFloat(), 0f, width * 0.7f, height * 0.2f, branchPaint)
    }

    private fun drawSeashells(canvas: Canvas, width: Int, height: Int) {
        val shellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60F4A460")
            style = Paint.Style.FILL
        }

        for (i in 0..10) {
            val x = (Math.random() * width).toFloat()
            val y = height * 0.8f + (Math.random() * height * 0.2f).toFloat()
            canvas.drawCircle(x, y, 8f, shellPaint)
        }
    }

    private fun drawElegantBorder(canvas: Canvas, width: Int, height: Int) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60FFD700")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Bordas elegantes
        canvas.drawRect(20f, 20f, width - 20f, height - 20f, borderPaint)
        canvas.drawRect(40f, 40f, width - 40f, height - 40f, borderPaint)
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()

            val file = File(cachePath, "frase_${System.currentTimeMillis()}.png")
            val fileOutputStream = FileOutputStream(file)

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    // ✅ FUNÇÃO CORRIGIDA: Compartilhar imagem COM texto
    private fun shareImageWithText(uri: Uri, phrase: Phrase) {
        // Criar texto da frase para acompanhar a imagem
        val shareText = "✨ Frase Inspiradora ✨\n\n" +
                "\"${phrase.text}\"\n\n" +
                "— ${phrase.reference}\n\n" +
                "📱 Baixe o app \"Frases que Despertam\""

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText) // ✅ Adicionar o texto junto com a imagem
            putExtra(Intent.EXTRA_SUBJECT, "Frase Inspiradora - ${phrase.reference}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, "Compartilhar frase inspiradora")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    // ✅ FUNÇÃO ALTERNATIVA: Para apps que suportam múltiplos itens
    private fun shareImageWithTextAlternative(uri: Uri, phrase: Phrase) {
        val shareText = "✨ Frase Inspiradora ✨\n\n" +
                "\"${phrase.text}\"\n\n" +
                "— ${phrase.reference}\n\n" +
                "📱 Baixe o app \"Frases que Despertam\""

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"

            // Lista com imagem e texto
            val uris = arrayListOf(uri)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Frase Inspiradora")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooserIntent = Intent.createChooser(intent, "Compartilhar frase")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }

    private fun shareAsText(phrase: Phrase) {
        val shareText = "${phrase.text}\n\n- ${phrase.reference}"
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Compartilhar frase"))
    }
}